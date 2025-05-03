package Modules.flamegraph;

import Modules.JobProfiler;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FlamegraphBuilder {

    private final long frameStartTime;
    private final float timeScale; // pixels per nanosecond
    private final float rowHeight = 20f;

    public FlamegraphBuilder(List<JobProfiler.FlameEvent> events, int screenWidth) {
        this.frameStartTime = events.stream()
                .mapToLong(JobProfiler.FlameEvent::startTime)
                .min()
                .orElse(System.nanoTime());

        long latestEnd = events.stream()
                .mapToLong(JobProfiler.FlameEvent::endTime)
                .max()
                .orElse(frameStartTime + 1);

        long frameDuration = (long)(Math.max(1, latestEnd - frameStartTime));
        this.timeScale = screenWidth / (float) frameDuration;
    }

    public List<FlameRect> build(List<JobProfiler.FlameEvent> events) {
        List<FlameRect> result = new ArrayList<>();
        Map<String, Integer> rowMap = new HashMap<>();
        AtomicInteger nextRow = new AtomicInteger();


        for (JobProfiler.FlameEvent event : events) {
            long relStart = event.startTime() - frameStartTime;
            long duration = event.endTime() - event.startTime();

            float x = relStart * timeScale;
            float width = Math.max(duration * timeScale, 2f); // still show 1-px marker

            // Special color for frame markers
            boolean isFrameMarker = event.jobName().equals("FRAME START") || event.jobName().equals("FRAME END");
            if(isFrameMarker){
                float[] color = new float[]{0.9f, 0f, 0f, 0.3f};
                if(event.jobName().equals("FRAME START")){

                    color = new float[]{0f, 0f, 0.9f, 0.3f};
                }
                float y = 0;
                result.add(new FlameRect(x, y, width, rowHeight*4, event.jobName(), color));

            }else {
                float[] color = new float[]{0.0f, 0.2f, 0.0f, 1.0f};

                int row = rowMap.computeIfAbsent(event.threadName(), k -> nextRow.getAndIncrement());
                float y = row * rowHeight;

                result.add(new FlameRect(x, y, width, rowHeight-2, event.jobName(), color));
            }
        }


        return result;
    }

}
