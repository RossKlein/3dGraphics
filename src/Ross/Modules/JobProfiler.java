package Ross.Modules;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // ---- Capture buffer (unbounded, only active while recording) -----------
    private static volatile boolean capturing = false;
    private static final Deque<FlameEvent> captureBuffer = new ConcurrentLinkedDeque<>();

    public static void startCapture() {
        captureBuffer.clear();
        capturing = true;
    }

    /** Stops capture and returns all accumulated events as a snapshot list. */
    public static List<FlameEvent> stopCapture() {
        capturing = false;
        return new ArrayList<>(captureBuffer);
    }
    // ------------------------------------------------------------------------

    public static void record(String jobName, long initTime, long startTime, long endTime,
                               String threadName, String flags) {
        while (events.size() >= MAX_EVENTS) {
            events.pollFirst();
        }
        FlameEvent e = new FlameEvent(jobName, initTime, startTime, endTime, threadName, flags);
        events.addLast(e);

        if (capturing) captureBuffer.addLast(e);
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
        FlameEvent e = new FlameEvent(label, timestamp, timestamp, timestamp + 1, threadName, "MARKER");
        events.addLast(e);
        if (capturing) captureBuffer.addLast(e);
    }

    /**
     * Dumps the given snapshot to {@code <tmpdir>/flamegraph_3d.txt} (overwrites
     * each time so you always get the data from the last held ENTER).
     *
     * Columns (tab-separated):
     *   jobName  thread  init_ms  start_ms  end_ms  duration_ms
     *
     * All timestamps are relative to the earliest initTime in the snapshot so
     * the numbers are human-readable rather than epoch nanoseconds.
     */
    public static void dumpToFile(List<FlameEvent> snapshot) {
        if (snapshot.isEmpty()) return;

        long t0 = snapshot.stream().mapToLong(FlameEvent::initTime).min().orElse(0L);
        Path path = Path.of(System.getProperty("java.io.tmpdir"), "flamegraph_3d.txt");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println("jobName\tflags\tthread\tinit_ms\twait_ms\tstart_ms\tend_ms\tduration_ms");
            for (FlameEvent e : snapshot) {
                double initMs     = (e.initTime()  - t0) / 1_000_000.0;
                double startMs    = (e.startTime() - t0) / 1_000_000.0;
                double endMs      = (e.endTime()   - t0) / 1_000_000.0;
                double durationMs = (e.endTime() - e.startTime()) / 1_000_000.0;
                double waitMs     = (e.startTime() - e.initTime()) / 1_000_000.0;
                pw.printf("%s\t%s\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f%n",
                        e.jobName(),
                        e.flags(),
                        e.threadName(),
                        initMs, waitMs, startMs, endMs, durationMs);
            }
        } catch (IOException ex) {
            System.err.println("[JobProfiler] flamegraph dump failed: " + ex.getMessage());
        }

        System.out.println("[JobProfiler] flamegraph written → " + path);
    }

    public record FlameEvent(String jobName, long initTime, long startTime, long endTime,
                              String threadName, String flags) {
    }
}
