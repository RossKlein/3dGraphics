package Ross.Modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe job queue for one phase of the engine loop (update or render).
 *
 * <h3>Two internal lists</h3>
 * <ul>
 *   <li>{@code pendingJobs} — ready to run immediately</li>
 *   <li>{@code heldJobs}    — waiting for cross-job dependencies
 *       ({@link Job#dependsOn}) to complete; moved to pending automatically
 *       by {@link #onJobComplete}</li>
 * </ul>
 *
 * <h3>GL-thread routing</h3>
 * Jobs with {@link Job#glThread} = true are NOT routed through this queue's
 * {@link #frameCall()} output. Instead, {@link JobModule#updateJobs} detects
 * the flag and moves them to {@link JobModule#glJobs} before submitting
 * anything to the worker pool.
 *
 * <h3>Frame call</h3>
 * {@link #frameCall()} drains all ready pending jobs, sorts them by
 * {@link Job.Priority} first and then by longest previous duration (work-
 * stealing heuristic), and returns the ordered list for submission.
 */
public class JobQueue {

    /** Jobs that are ready to run — drained each frame. */
    private final ConcurrentLinkedQueue<Job> pendingJobs = new ConcurrentLinkedQueue<>();

    /**
     * Jobs whose {@link Job#dependsOn} list is not yet fully complete.
     * {@link #onJobComplete} moves eligible jobs to {@link #pendingJobs}.
     *
     * Iteration with removal is O(n) on ConcurrentLinkedQueue, which is
     * acceptable for the small number of in-flight chunk jobs.
     */
    private final ConcurrentLinkedQueue<Job> heldJobs = new ConcurrentLinkedQueue<>();

    public JobQueue() { }

    // ---- Assignment -------------------------------------------------------

    /**
     * Add a job to the queue.
     * <ul>
     *   <li>If all dependencies are already complete, it goes straight to
     *       {@code pendingJobs}.</li>
     *   <li>Otherwise it goes to {@code heldJobs} and will be promoted when
     *       {@link #onJobComplete} fires.</li>
     * </ul>
     */
    public void assign(Job job) {
        // Stamp dispatch time so wait_ms = start - initTime reflects actual
        // scheduler latency for this tick, even for singleton/reused jobs.
        job.initTime = System.nanoTime();
        if (job.isReady()) {
            pendingJobs.add(job);
        } else {
            heldJobs.add(job);
        }
    }

    // ---- Dependency resolution --------------------------------------------

    /**
     * Called by {@link Job} via its {@code postExecute()} when a job finishes
     * (including all its subtasks). Promotes any held job whose dependencies
     * are now fully satisfied.
     *
     * Thread-safe: may be called from worker threads or the GL thread.
     */
    public void onJobComplete(Job completed) {
        List<Job> nowReady = null;
        for (Job held : heldJobs) {
            if (held.isReady()) {
                if (nowReady == null) nowReady = new ArrayList<>();
                nowReady.add(held);
            }
        }
        if (nowReady == null) return;
        for (Job ready : nowReady) {
            heldJobs.remove(ready);
            if (!ready.isCancelled()) {
                pendingJobs.add(ready);
            }
        }
    }

    // ---- Frame call -------------------------------------------------------

    /**
     * Drain all currently-pending jobs, sort them, and return as an ordered
     * list for this frame. Held jobs are not included — they will appear in a
     * future frame once their dependencies complete.
     *
     * Sort order: {@link Job.Priority} ascending (CRITICAL first), then
     * longest previous duration first within the same tier.
     */
    public LinkedList<Job> frameCall() {
        List<Job> collected = new ArrayList<>();

        Job job;
        while ((job = pendingJobs.poll()) != null) {
            collected.add(job);
        }

        Collections.sort(collected);   // relies on Job.compareTo() — priority tier, then duration

        return new LinkedList<>(collected);
    }

    // ---- Diagnostics ------------------------------------------------------

    /** Total jobs in both queues (pending + held). */
    public int size() {
        return pendingJobs.size() + heldJobs.size();
    }

    /** Jobs currently waiting on cross-job dependencies. */
    public int heldCount() {
        return heldJobs.size();
    }
}
