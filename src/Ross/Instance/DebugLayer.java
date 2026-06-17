package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.JobProfiler;
import Ross.Modules.Settings;
import Ross.Modules.flamegraph.FlameRect;
import Ross.Modules.flamegraph.FlamegraphBuilder;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.RenderTarget;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;

/**
 * Flamegraph profiler overlay — always on top of the SCREEN stack (order 99).
 *
 * Controls (same as the old testscene):
 *   ENTER      — snapshot the current frame and display the flamegraph
 *   BACKSPACE  — clear profiler data and hide the overlay
 *
 * This is the first concrete Layer in the engine and verifies the LayerManager
 * pipeline end-to-end.
 *
 * Threading:
 *   update job  — checks key input, builds List&lt;FlameRect&gt; (pure CPU, no GL)
 *   render()    — rebuilds the GL Model when rects change, then draws it
 */
public class DebugLayer extends BaseLayer {

    // Stored on onPush(); JobModule is a singleton that lives for the engine lifetime.
    private JobModule jobs;

    private final ModelBuilder modelBuilder = new ModelBuilder();
    private final InputJob     inputJob     = new InputJob();

    // Handoff from update thread → render thread.
    // volatile list reference provides a happens-before guarantee so the render
    // thread sees the fully-constructed list contents.
    private volatile List<FlameRect> pendingRects = null;  // non-null = rebuild GL model
    private volatile boolean         resetPending  = false;

    // Owned exclusively by the GL (render) thread after the first build.
    private Model flamegraphModel = null;

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.SCREEN; }
    @Override public int    getOrder() { return 99; }
    @Override public String getName()  { return "debug"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override
    public List<Job> onPush(JobModule jobs) {
        this.jobs = jobs;
        return Collections.emptyList();
    }

    // ---- Update (non-GL, runs on thread pool) ------------------------------

    @Override
    public List<Job> update(JobModule jobs) {
        return List.of(inputJob);
    }

    private class InputJob extends Job {
        private boolean wasEnterDown = false;

        @Override
        public void code() {
            boolean enterDown = jobs.inputHandler.isKeyDown(GLFW.GLFW_KEY_ENTER);

            if (enterDown && !wasEnterDown) {
                // Key just pressed — start the unbounded capture buffer.
                JobProfiler.startCapture();
            }

            if (enterDown) {
                // Refresh the visual overlay from the normal ring buffer each tick.
                List<JobProfiler.FlameEvent> ring = JobProfiler.getFlamegraphEvents();
                FlamegraphBuilder builder = new FlamegraphBuilder(ring, Settings.width);
                pendingRects = builder.build(ring);
            } else if (wasEnterDown) {
                // Key just released — stop capture and write the full buffer once.
                List<JobProfiler.FlameEvent> full = JobProfiler.stopCapture();
                JobProfiler.dumpToFile(full);
            }

            wasEnterDown = enterDown;

            if (jobs.inputHandler.isKeyDown(GLFW.GLFW_KEY_BACKSPACE)) {
                resetPending = true;
            }
        }
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {

        // Reset takes priority — clears the model before any pending build
        if (resetPending) {
            JobProfiler.reset();
            if (flamegraphModel != null) {
                flamegraphModel.dispose();
                flamegraphModel = null;
            }
            pendingRects = null;
            resetPending  = false;
        }

        // Rebuild GL model from rects produced by the last update job
        List<FlameRect> rects = pendingRects;
        if (rects != null) {
            if (flamegraphModel != null) flamegraphModel.dispose();
            flamegraphModel = FlameRect.buildFlamegraphModel(rects, modelBuilder);
            pendingRects = null; // consume
        }

        if (flamegraphModel == null) return Collections.emptyList();

        // Pixel-exact orthographic projection, (0,0) at top-left
        float w = target.getWidth();
        float h = target.getHeight();
        Mat4f ortho = new Mat4f().orthographic(0, w, h, 0, -1, 1);

        jobs.renderer.loadMatrix(new Mat4f().identity(), "m", false, 0);
        jobs.renderer.loadMatrix(new Mat4f().identity(), "v", false, 1);
        jobs.renderer.loadMatrix(ortho,                  "p", false, 2);
        jobs.renderer.loadLightSource(new Vec3f(0, 0, 1));
        jobs.renderer.addBindBool("useFlatColor", true);
        jobs.renderer.renderModel(flamegraphModel);

        return Collections.emptyList();
    }
}
