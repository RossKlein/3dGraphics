package Modules;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Job implements Comparable<Job> {

    private JobQueue jobQueue;
    private final List<Job> subtasks = new ArrayList<>();

    private Job parent = null;
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile boolean isWaiting = false;

    protected long lastDuration = 0;
    protected final long initTime;

    public Job() {
        this.initTime = System.nanoTime();  // For flamegraph profiling kind of unnecessary tho
    }

    public abstract void code();

    public Runnable asRunnable(JobQueue jobQueue) {
        this.jobQueue = jobQueue;
        return () -> {
            long start = System.nanoTime();

            this.code();  // core job logic

            long end = System.nanoTime();
            lastDuration = end - start;
            String threadName = Thread.currentThread().getName();
            JobProfiler.record(this.getClass().getSimpleName(), initTime, start, end, threadName);

            if (counter.get() > 0) {
                isWaiting = true;
            } else {
                postExecute();
            }
        };
    }

    /**
     * Optional override point: run after subtasks complete
     */
    public void postCode() {
        // no-op by default
    }

    private void postExecute() {
        postCode();

        if (parent != null) {
            parent.decCounter();
        }
    }

    public void addSubtask(Job subtask) {
        subtask.setParent(this);
        subtasks.add(subtask);
        counter.incrementAndGet();
        jobQueue.assign(subtask);
    }

    private void setParent(Job parent) {
        this.parent = parent;
    }

    public void decCounter() {
        int remaining = counter.decrementAndGet();
        if (remaining == 0 && isWaiting) {
            isWaiting = false;
            jobQueue.addWaiting(this);
        }
    }

    public List<Job> getSubtasks() {
        return subtasks;
    }

    public boolean isWaiting() {
        return isWaiting;
    }

    public long getLastDuration() {
        return lastDuration;
    }

    public long getInitTime() {
        return initTime;
    }

    public JobQueue getJobQueue() {
        return jobQueue;
    }

    @Override
    public int compareTo(Job other) {
        return Long.compare(other.lastDuration, this.lastDuration); // longest first
    }
}
