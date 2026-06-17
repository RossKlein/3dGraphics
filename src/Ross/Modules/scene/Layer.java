package Ross.Modules.scene;

import Ross.Modules.Job;
import Ross.Modules.JobModule;

import java.util.List;

/**
 * A Layer is the fundamental unit of game logic and rendering in v4,
 * replacing the old single-Scene approach.
 *
 * Multiple layers are active simultaneously each frame. They are divided
 * into two groups by their render target:
 *
 *   WORLD  — renders into the offscreen world FBO (affected by post-processing)
 *   SCREEN — renders directly to the screen after post-processing completes
 *
 * Layers are ordered within their group by {@link #getOrder()} (lower = drawn first).
 * {@link LayerManager} owns the stack and drives the pipeline.
 *
 * State machine:
 *   ACTIVE  → update() and render() both run
 *   PAUSED  → only render() runs  (world stays visible but frozen behind a menu)
 *   HIDDEN  → neither runs
 */
public interface Layer {

    enum Group { WORLD, SCREEN }

    enum State { ACTIVE, PAUSED, HIDDEN }

    Group  getGroup();
    int    getOrder();
    String getName();
    State  getState();
    void   setState(State state);

    /**
     * Called once by {@link LayerManager#push} when this layer is added to the stack.
     *
     * <strong>No GL calls permitted here.</strong> push() is called before the GL
     * context exists (layers are pushed in Main before start()). Use this for
     * storing references and kicking off CPU-only work (file parsing, etc.).
     *
     * GL initialization (VAO/VBO creation, shader compilation, FBO setup) must be
     * deferred to the first {@link #render} call, using a {@code boolean initialized}
     * guard — the same pattern used by {@link LayerManager} and ControlsHudLayer.
     *
     * Return any CPU-only initialization jobs (asset file I/O, procedural data
     * generation, etc.). These will be submitted to the thread pool.
     */
    List<Job> onPush(JobModule jobs);

    /**
     * Called once by {@link LayerManager#pop} when this layer is removed.
     * Return any cleanup jobs.
     */
    List<Job> onPop(JobModule jobs);

    /**
     * Called each update tick when state == ACTIVE.
     * Returns jobs to submit to the update thread pool.
     */
    List<Job> update(JobModule jobs);

    /**
     * Called each render frame when state != HIDDEN.
     *
     * The appropriate render target is already bound by LayerManager before
     * this method is called — just issue GL draw calls.
     * The target is passed so the layer can query its dimensions for
     * projection matrices without holding a window reference.
     *
     * Return any additional non-GL jobs to run in parallel (e.g. updating
     * per-frame CPU data for the next frame).
     */
    List<Job> render(JobModule jobs, RenderTarget target, double deltaTime);
}
