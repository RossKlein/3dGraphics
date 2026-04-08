package Ross.Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract unit of work for the engine's job system.
 *
 * <h3>Priority</h3>
 * Assign {@link #priority} before adding to a queue. The queue sorts CRITICAL
 * first, IDLE last, and breaks ties by longest previous duration (work-stealing
 * heuristic).
 *
 * <h3>GL-thread pinning</h3>
 * Set {@link #glThread} = true for any job that calls OpenGL (VAO/VBO creation,
 * shader uniforms, etc.). {@link JobModule#updateJobs} will route these to the
 * {@link JobModule#glJobs} queue instead of the worker pool. They are drained
 * synchronously at the start of each render frame before any layer renders.
 *
 * <h3>Cancellation</h3>
 * Assign a {@link CancelToken} and check {@link #isCancelled()} at safe points
 * inside {@link #code()}. A cancelled job returns early without doing its work.
 * Cancelled dependent jobs (in {@link #dependsOn}) are skipped automatically.
 *
 * <h3>Cross-job dependencies</h3>
 * Populate {@link #dependsOn} before assigning to a queue. The queue holds the
 * job in a "held" set until all listed jobs are {@link #isComplete()}. Example:
 * <pre>{@code
 * Job gen    = new ChunkGenerateJob(chunk);
 * Job upload = new ChunkUploadJob(chunk);
 * upload.glThread   = true;
 * upload.dependsOn.add(gen);
 * queue.assign(gen);
 * queue.assign(upload);   // held until gen completes
 * }</pre>
 *
 * <h3>Subtask tree (intra-job parallelism)</h3>
 * Calling {@link #addSubtask(Job)} inside {@link #code()} forks child jobs.
 * The parent's {@link #postCode()} and completion notification fire only after
 * all subtasks have finished.
 */
public abstract class Job implements Comparable<Job> {

    // ---- Priority ---------------------------------------------------------

    public enum Priority {
        CRITICAL,   // flight physics, camera — must complete this frame
        HIGH,       // near-field chunk generation, LOD updates
        NORMAL,     // load-ahead chunks, standard scene work
        LOW,        // prefetch / background generation
        IDLE        // deferred if frame is over time budget
    }

    /** Controls queue ordering. Default is {@link Priority#NORMAL}. */
    public Priority priority = Priority.NORMAL;

    // ---- GL-thread pinning ------------------------------------------------

    /**
     * When true this job is routed to {@link JobModule#glJobs} and runs
     * synchronously on the render/GL thread instead of the worker pool.
     */
    public boolean glThread = false;

    // ---- Background (fire-and-forget) -------------------------------------

    /**
     * When true the job runs on the worker pool but is NOT counted in the
     * per-tick {@link java.util.concurrent.CountDownLatch}.  The update loop
     * therefore does not block waiting for it to finish.
     *
     * Use for long-running work whose result is consumed asynchronously
     * (e.g. chunk generation — the GL upload job fires via
     * {@link JobQueue#onJobComplete} when this job completes).
     */
    public boolean background = false;

    // ---- Cancellation -----------------------------------------------------

    /**
     * Optional cancellation handle. Set before assigning to a queue.
     * The job should call {@link #isCancelled()} at safe points and return early.
     */
    public volatile CancelToken cancelToken = null;

    /** True if a cancel token exists and has been cancelled. */
    public boolean isCancelled() {
        return cancelToken != null && cancelToken.isCancelled();
    }

    // ---- Cross-job dependencies -------------------------------------------

    /**
     * Jobs that must be complete before this job becomes eligible to run.
     * Populate before calling {@link JobQueue#assign(Job)}.
     */
    public final List<Job> dependsOn = new ArrayList<>();

    /**
     * Returns true when every job in {@link #dependsOn} has completed.
     * An empty list means the job is immediately ready.
     */
    public boolean isReady() {
        for (Job dep : dependsOn) {
            if (!dep.complete) return false;
        }
        return true;
    }

    // ---- Completion state -------------------------------------------------

    /**
     * Set to true by {@link #postExecute()} after this job (and all its
     * subtasks) have finished. Read by dependent jobs via {@link #isReady()}.
     */
    volatile boolean complete = false;

    public boolean isComplete() { return complete; }

    // ---- Internal state ---------------------------------------------------

    private JobQueue jobQueue;

    private final List<Job> subtasks   = new ArrayList<>();
    private Job              parent     = null;

    /** Counts running subtasks. When it reaches 0 the parent can finish. */
    private final AtomicInteger pendingSubtasks = new AtomicInteger(0);
    private volatile boolean    waitingOnSubtasks = false;

    protected long lastDuration = 0;

    /**
     * Nanosecond timestamp of when this job was last dispatched (assigned to a
     * queue). Reset by {@link JobQueue#assign} each time the job is enqueued,
     * so singleton jobs (controls, matrixWork) get an accurate per-tick
     * wait_ms in the profiler rather than a stale timestamp from construction.
     */
    long initTime;

    /**
     * Human-readable job name recorded in the profiler.
     * Defaults to the class simple name; override for anonymous subclasses:
     * <pre>
     *   Job j = new Job() { { name = "ChunkGenerate[0,0]"; } ... };
     * </pre>
     */
    public String name;

    // -----------------------------------------------------------------------

    public Job() {
        this.initTime = System.nanoTime();  // overwritten by JobQueue.assign() on each dispatch
        String simple = getClass().getSimpleName();
        this.name = (simple == null || simple.isEmpty()) ? "(anonymous)" : simple;
    }

    /**
     * Short flag string used in the profiler dump.
     * <ul>
     *   <li>{@code GL} — pinned to the render/GL thread</li>
     *   <li>{@code BG} — background (fire-and-forget, never blocks the tick)</li>
     *   <li>{@code FG} — normal foreground job (tick waits for completion)</li>
     * </ul>
     */
    public String flags() {
        if (glThread)   return "GL";
        if (background) return "BG";
        return "FG";
    }

    /** Override with the actual work. Must be safe to run on the worker pool
     *  unless {@link #glThread} is true. */
    public abstract void code();

    /**
     * Called once, after {@link #code()} and after all subtasks complete.
     * Override to do post-processing or to chain follow-up jobs.
     */
    public void postCode() { }

    // ---- Execution --------------------------------------------------------

    /**
     * Wraps this job as a {@link Runnable} for submission to a thread pool or
     * direct execution. Sets the back-reference to the queue so subtasks and
     * dependency notifications route correctly.
     *
     * @param queue the queue this job belongs to (used for subtask assignment
     *              and completion notifications)
     */
    public Runnable asRunnable(JobQueue queue) {
        this.jobQueue = queue;
        return () -> {
            long start = System.nanoTime();

            try {
                code();
            } catch (Throwable t) {
                // Log but do NOT rethrow — postExecute() must always run so
                // dependent jobs (e.g. upload waiting on generate) are not
                // left orphaned in heldJobs forever, causing permanent holes.
                System.err.println("[Job] Uncaught exception in '" + name + "': " + t);
                t.printStackTrace(System.err);
            }

            long end = System.nanoTime();
            lastDuration = end - start;
            JobProfiler.record(
                    name,
                    initTime, start, end,
                    Thread.currentThread().getName(),
                    flags());

            if (pendingSubtasks.get() > 0) {
                // Subtasks still running — defer postExecute until the last
                // subtask calls decSubtaskCounter().
                waitingOnSubtasks = true;
            } else {
                postExecute();
            }
        };
    }

    /**
     * Final step after all work (code + subtasks) is done.
     * Marks complete, notifies the queue's dependency graph, runs postCode,
     * and notifies the parent job if this was a subtask.
     *
     * Must only be called once per job instance.
     */
    private void postExecute() {
        complete = true;
        jobQueue.onJobComplete(this);
        postCode();
        if (parent != null) {
            parent.decSubtaskCounter();
        }
    }

    // ---- Subtask tree -----------------------------------------------------

    /**
     * Forks a child job. The child is immediately assigned to the same queue.
     * This job will not call {@link #postCode()} until all subtasks complete.
     * Must be called from within {@link #code()}.
     */
    public void addSubtask(Job subtask) {
        subtask.setParent(this);
        subtasks.add(subtask);
        pendingSubtasks.incrementAndGet();
        jobQueue.assign(subtask);
    }

    private void setParent(Job p) { this.parent = p; }

    /**
     * Called by a finishing subtask. When the count reaches zero and this job
     * was waiting, trigger the deferred {@link #postExecute()}.
     */
    void decSubtaskCounter() {
        int remaining = pendingSubtasks.decrementAndGet();
        if (remaining == 0 && waitingOnSubtasks) {
            waitingOnSubtasks = false;
            postExecute();
        }
    }

    // ---- Accessors --------------------------------------------------------

    public List<Job>  getSubtasks()      { return subtasks; }
    public boolean    isWaiting()        { return waitingOnSubtasks; }
    public long       getLastDuration()  { return lastDuration; }
    public long       getInitTime()      { return initTime; }
    public JobQueue   getJobQueue()      { return jobQueue; }

    // ---- Ordering ---------------------------------------------------------

    /**
     * Higher priority runs first. Within the same priority tier, longer
     * previous duration runs first (work-stealing heuristic).
     */
    @Override
    public int compareTo(Job other) {
        int pc = Integer.compare(this.priority.ordinal(), other.priority.ordinal());
        if (pc != 0) return pc;                                   // lower ordinal = higher priority
        return Long.compare(other.lastDuration, this.lastDuration); // longer first within tier
    }
}
