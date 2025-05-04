package Ross.Modules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JobQueue {

    private final Time time;

    // Flat job pool — thread-safe
    private final ConcurrentLinkedQueue<Job> pendingJobs = new ConcurrentLinkedQueue<>();

    // Jobs that were waiting on subtasks
    private final ConcurrentLinkedQueue<Job> waitingJobs = new ConcurrentLinkedQueue<>();

    public JobQueue(Time time) {
        this.time = time;
    }

    /**
     * Assign a job to the queue.
     */
    public void assign(Job job) {
        pendingJobs.add(job);
    }

    /**
     * Re-submit a completed dependency job (from a subtask).
     */
    public void addWaiting(Job job) {
        waitingJobs.add(job);
    }

    /**
     * Collect jobs for this frame and return them sorted by longest duration first.
     */
    public LinkedList<Job> frameCall() {
        List<Job> collected = new ArrayList<>();

        // Drain waiting jobs first — these are usually critical
        while (!waitingJobs.isEmpty()) {
            Job job = waitingJobs.poll();
            if (job != null) collected.add(job);
        }

        // Drain normal jobs
        while (!pendingJobs.isEmpty()) {
            Job job = pendingJobs.poll();
            if (job != null) collected.add(job);
        }

        // Sort by duration (longest first)
        Collections.sort(collected); // relies on Job.compareTo()

        return new LinkedList<>(collected);
    }

    // Optionally expose for debugging or diagnostics
    public int size() {
        return pendingJobs.size() + waitingJobs.size();
    }
}
