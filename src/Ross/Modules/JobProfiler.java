package Ross.Modules;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

public class JobProfiler {

    private static final ConcurrentHashMap<String, LongAdder> durations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private static final int MAX_EVENTS = 100;
    private static final Deque<FlameEvent> events = new ConcurrentLinkedDeque<>();

    public static void record(String jobName, long initTime, long startTime, long endTime, String threadName) {
//        durations.computeIfAbsent(jobName, k -> new LongAdder()).add(endTime - startTime);
//        counts.computeIfAbsent(jobName, k -> new LongAdder()).increment();

        while (events.size() >= MAX_EVENTS) {
            events.pollFirst(); // remove oldest
        }

        events.addLast(new FlameEvent(jobName, initTime, startTime, endTime, threadName));
    }

    public static long getAverage(String jobName) {
        long total = durations.getOrDefault(jobName, new LongAdder()).sum();
        long count = counts.getOrDefault(jobName, new LongAdder()).sum();
        return (count == 0) ? 0 : total / count;
    }

    public static List<FlameEvent> getFlamegraphEvents() {
        return new ArrayList<>(events);
    }

    public static void reset() {
        durations.clear();
        counts.clear();
        events.clear();
    }

    public static void markFrame(String label, long timestamp, String threadName) {
        events.add(new FlameEvent(label, timestamp, timestamp, timestamp + 1, threadName));
    }

    public record FlameEvent(String jobName, long initTime, long startTime, long endTime, String threadName) {
    }
}
