package Ross.Modules.scene;

import Ross.Modules.JobModule;

/**
 * Named scene transitions — each method pushes a coherent set of layers onto
 * the LayerManager to represent one "scene" in the traditional sense.
 *
 * There is no SceneManager class: transitioning between scenes is just push/pop
 * on the layer stack. This keeps the architecture flat and composable.
 *
 * Concrete layer classes (SkyLayer, WorldLayer, HudLayer, etc.) are added here
 * as they are implemented in later milestones. The method signatures are stable
 * so callers don't need to change.
 *
 * Typical startup in Main:
 * <pre>{@code
 * LayerManager lm = new LayerManager();
 * GameState    gs = new GameState();
 * Scenes.loadMainMenu(lm, jobs);
 * jobModule.start(lm);
 * }</pre>
 *
 * Transitioning from main menu to gameplay (e.g. on a "Start" button click):
 * <pre>{@code
 * lm.pop("main-menu", jobs);
 * Scenes.loadGameplay(lm, jobs, gameState);
 * }</pre>
 */
public class Scenes {

    private Scenes() {}   // static utility

    // ---- Scene transitions -------------------------------------------------

    /**
     * Push the main menu layer set.
     * Currently a placeholder; MainMenuLayer is implemented with RmlUi in a later milestone.
     */
    public static void loadMainMenu(LayerManager lm, JobModule jobs) {
        // lm.push(new MainMenuLayer(), jobs);     // SCREEN, order 0
    }

    /**
     * Push the full gameplay layer set.
     * Layers are added in render order within their group (lower = drawn first).
     *
     * @param state  shared volatile state; all layers read/write the same instance.
     */
    public static void loadGameplay(LayerManager lm, JobModule jobs, GameState state) {
        // lm.push(new SkyLayer(state),          jobs);   // WORLD,  order 0
        // lm.push(new WorldLayer(state, jobs),  jobs);   // WORLD,  order 1
        // lm.push(new ParticleLayer(state),     jobs);   // WORLD,  order 2
        // lm.push(new HudLayer(state),          jobs);   // SCREEN, order 0
    }

    /**
     * Overlay the pause menu on top of a frozen world.
     * WorldLayer and HudLayer stay on the stack but stop updating.
     */
    public static void pushPauseMenu(LayerManager lm, JobModule jobs) {
        lm.setState("world",  Layer.State.PAUSED);
        lm.setState("sky",    Layer.State.PAUSED);
        lm.setState("hud",    Layer.State.HIDDEN);
        // lm.push(new PauseMenuLayer(), jobs);            // SCREEN, order 10
    }

    /** Resume gameplay by removing the pause menu and restoring layer states. */
    public static void popPauseMenu(LayerManager lm, JobModule jobs) {
        lm.pop("pause-menu", jobs);
        lm.setState("world", Layer.State.ACTIVE);
        lm.setState("sky",   Layer.State.ACTIVE);
        lm.setState("hud",   Layer.State.ACTIVE);
    }

    /**
     * Push a transient loading overlay on top of whatever is currently active.
     * The loading layer removes itself once the world reports it is ready.
     */
    public static void pushLoadingScreen(LayerManager lm, JobModule jobs) {
        // lm.push(new LoadingLayer(), jobs);              // SCREEN, order 5
    }
}
