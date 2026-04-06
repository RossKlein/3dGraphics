package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.Settings;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.RenderTarget;
import Ross.Modules.ui.RmlUi;

import java.util.Collections;
import java.util.List;

/**
 * SCREEN layer (order 0) — RmlUi controls reference and live camera stats.
 *
 * Displays:
 *   Top-left  — static key binding reference (WASD, Q/E, mouse, scroll, profiler keys)
 *   Bot-right — live camera data: FOV, X/Y/Z position  (data-model="camera")
 *
 * Font requirement:
 *   Place Roboto-Regular.ttf in res/ui/fonts/ before running.
 *   See res/ui/fonts/README.txt for download instructions.
 *   RmlUi silently skips text rendering if no font is loaded.
 *
 * Initialization is lazy (first render call) because the GL context does not
 * exist when onPush() is called from Main before start().
 */
public class ControlsHudLayer extends BaseLayer {

    private RmlUi  rmlUi       = null;
    private long   controlsDoc = 0;
    private boolean initialized = false;

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.SCREEN; }
    @Override public int    getOrder() { return 0; }
    @Override public String getName()  { return "controls-hud"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (rmlUi != null) {
            if (controlsDoc != 0) rmlUi.closeDocument(controlsDoc);
            rmlUi.shutdown();
            rmlUi = null;
        }
        return Collections.emptyList();
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {
        if (!initialized) {
            init(target.getWidth(), target.getHeight());
        }

        if (rmlUi == null) return Collections.emptyList();

        // Push live camera values into the data model each frame.
        // RmlUi re-renders template expressions ({{fov}} etc.) on the next update().
        rmlUi.setModelFloat("camera", "fov",  jobs.fov);
        rmlUi.setModelFloat("camera", "posX", jobs.position.x());
        rmlUi.setModelFloat("camera", "posY", jobs.position.y());
        rmlUi.setModelFloat("camera", "posZ", jobs.position.z());

        // Process layout (must happen before render)
        rmlUi.update();

        // Fill command buffer + upload geometry + issue draw calls
        rmlUi.render();

        return Collections.emptyList();
    }

    // ---- GL init (called once on first render frame) -----------------------

    private void init(int width, int height) {
        initialized = true;   // set immediately — don't retry on failure

        try {
            rmlUi = new RmlUi(width, height);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[ControlsHudLayer] librmlui_java not found — " +
                               "build native/build.sh and add natives/ to java.library.path");
            rmlUi = null;
            return;
        }

        // Font must be loaded before any document that contains text.
        // Silently continues if file is missing — RmlUi won't crash, just no text.
        boolean fontOk = rmlUi.loadFont("res/ui/fonts/Roboto-Regular.ttf");
        if (!fontOk) {
            System.err.println("[ControlsHudLayer] Font not found: res/ui/fonts/Roboto-Regular.ttf");
            System.err.println("  → Download Roboto from fonts.google.com, place in res/ui/fonts/");
        }

        // Register data model: float vars for camera stats, no string vars
        rmlUi.createDataModel("camera",
            new String[]{"fov", "posX", "posY", "posZ"},
            new String[]{});

        // Load and show the controls document
        controlsDoc = rmlUi.loadDocument("res/ui/controls.rml");
        if (controlsDoc != 0) {
            rmlUi.showDocument(controlsDoc);
        } else {
            System.err.println("[ControlsHudLayer] Failed to load res/ui/controls.rml");
        }
    }
}
