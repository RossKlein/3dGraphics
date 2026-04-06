package Ross.Modules.scene;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.Settings;

import static org.lwjgl.opengl.GL33.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Owns the active layer stack and drives the per-frame two-group render pipeline.
 *
 * Pipeline (each render frame):
 * <pre>
 *   WORLD layers  → worldFBO  → PostProcessChain → screen
 *   SCREEN layers → screen (drawn on top of post-processed world)
 * </pre>
 *
 * GL resources (worldFBO, PostProcessChain) are created lazily on the first
 * {@link #renderFrame} call so that the LayerManager can be constructed before
 * the GL context exists.
 *
 * Usage:
 * <pre>{@code
 * LayerManager lm = new LayerManager();
 * Scenes.loadGameplay(lm, jobs, gameState);   // pushes initial layers
 * jobModule.start(lm);                         // enters engine loop
 * }</pre>
 */
public class LayerManager {

    private final List<Layer> layers = new ArrayList<>();

    private Framebuffer      worldFBO;
    private ScreenTarget     screenTarget;
    private PostProcessChain postProcessChain;
    private boolean          initialized = false;

    // ---- Layer stack management --------------------------------------------

    /**
     * Push a layer onto the stack. Layers are kept sorted by order within their group.
     * Returns the jobs from {@link Layer#onPush} so the caller can run them if needed.
     */
    public List<Job> push(Layer layer, JobModule jobs) {
        layers.add(layer);
        layers.sort(Comparator.comparingInt(Layer::getOrder));
        return layer.onPush(jobs);
    }

    /** Remove a layer by reference. */
    public List<Job> pop(Layer layer, JobModule jobs) {
        if (layers.remove(layer))
            return layer.onPop(jobs);
        return List.of();
    }

    /** Remove the first layer with this name. */
    public List<Job> pop(String name, JobModule jobs) {
        Layer found = get(name);
        if (found != null) return pop(found, jobs);
        return List.of();
    }

    public Layer get(String name) {
        for (Layer l : layers)
            if (l.getName().equals(name)) return l;
        return null;
    }

    public void setState(String name, Layer.State state) {
        Layer l = get(name);
        if (l != null) l.setState(state);
    }

    // ---- Per-frame ---------------------------------------------------------

    /**
     * Collect update jobs from all ACTIVE layers.
     * Called by {@link JobModule#updateJobs} on the update thread.
     */
    public List<Job> collectUpdateJobs(JobModule jobs) {
        List<Job> all = new ArrayList<>();
        for (Layer l : layers)
            if (l.getState() == Layer.State.ACTIVE)
                all.addAll(l.update(jobs));
        return all;
    }

    /**
     * Run the full render pipeline for one frame.
     * Must be called on the GL thread (from {@link JobModule#renderJobs}).
     *
     * Returns any additional non-GL jobs the layers want to run in parallel
     * (e.g. precomputing data for the next frame).
     */
    public List<Job> renderFrame(JobModule jobs, double dt) {
        ensureInitialized();

        List<Job> extraJobs = new ArrayList<>();

        // 1. Render all WORLD layers into the world FBO
        worldFBO.bind();
        glViewport(0, 0, worldFBO.getWidth(), worldFBO.getHeight());
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);

        for (Layer l : layers) {
            if (l.getGroup() == Layer.Group.WORLD && l.getState() != Layer.State.HIDDEN)
                extraJobs.addAll(l.render(jobs, worldFBO, dt));
        }

        // 2. Post-process: worldFBO → screen  (leaves default FBO bound)
        postProcessChain.run(worldFBO);

        // 3. Render all SCREEN layers directly on top of the post-processed frame
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, screenTarget.getWidth(), screenTarget.getHeight());
        glDisable(GL_DEPTH_TEST);

        for (Layer l : layers) {
            if (l.getGroup() == Layer.Group.SCREEN && l.getState() != Layer.State.HIDDEN)
                extraJobs.addAll(l.render(jobs, screenTarget, dt));
        }

        glEnable(GL_DEPTH_TEST);

        return extraJobs;
    }

    // ---- Resize ------------------------------------------------------------

    /**
     * Call when the window is resized. Recreates the world FBO and notifies
     * the post-process chain. Must be called on the GL thread.
     */
    public void resize(int width, int height) {
        if (!initialized) return;
        worldFBO.dispose();
        worldFBO = new Framebuffer(width, height);
        screenTarget.resize(width, height);
        postProcessChain.resize(width, height);
    }

    // ---- Post-process access -----------------------------------------------

    /** Access the post-process chain to add/remove passes at runtime. */
    public PostProcessChain getPostProcessChain() {
        return postProcessChain;
    }

    // ---- Cleanup -----------------------------------------------------------

    public void dispose() {
        for (Layer l : layers) l.onPop(null);
        if (worldFBO         != null) worldFBO.dispose();
        if (postProcessChain != null) postProcessChain.dispose();
    }

    // ---- Lazy GL init ------------------------------------------------------

    /**
     * Creates GL resources on the first render frame.
     * Cannot be done in the constructor because the GL context may not exist yet.
     */
    private void ensureInitialized() {
        if (initialized) return;
        int w = Settings.width;
        int h = Settings.height;
        worldFBO         = new Framebuffer(w, h);
        screenTarget     = new ScreenTarget(w, h);
        postProcessChain = new PostProcessChain();
        postProcessChain.init(w, h);
        initialized = true;
    }
}
