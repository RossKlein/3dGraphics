package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.GameState;
import Ross.Modules.scene.RenderTarget;
import Ross.Modules.ui.RmlEventListener;
import Ross.Modules.ui.RmlUi;

import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;

/**
 * SCREEN layer (order 0) — RmlUi controls reference and live camera stats.
 * When paused (Esc), swaps to {@code res/ui/pause.rml} with a resume control,
 * shows the normal cursor, and forwards mouse input to RmlUi.
 *
 * Time-of-day controls (work in both play and pause):
 *   -   decrease by one hour
 *   =   increase by one hour
 */
public class ControlsHudLayer extends BaseLayer {

    private final GameState state;

    private RmlUi   rmlUi       = null;
    private long    controlsDoc = 0;
    private long    pauseDoc    = 0;
    private boolean initialized  = false;
    private boolean escWasDown   = false;
    private boolean minusWasDown = false;
    private boolean equalWasDown = false;

    // Rolling average FPS — exponential moving average, α = 0.05
    private float avgFps = 0f;
    private final boolean[] prevMouseButtons = new boolean[8];

    private static final float TIME_STEP = 1f / 96f;   // 15 minutes per keypress

    public ControlsHudLayer(GameState state) {
        this.state = state;
    }

    private final RmlEventListener uiEvents = (elementId, eventType, value) -> {
        if ("resume-btn".equals(elementId) && "click".equals(eventType)) {
            resumeFromPause();
        }
    };

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.SCREEN; }
    @Override public int    getOrder() { return 0; }
    @Override public String getName()  { return "controls-hud"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (rmlUi != null) {
            if (controlsDoc != 0) rmlUi.closeDocument(controlsDoc);
            if (pauseDoc != 0) rmlUi.closeDocument(pauseDoc);
            rmlUi.shutdown();
            rmlUi = null;
        }
        return Collections.emptyList();
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {
        if (!initialized) {
            init(target.getWidth(), target.getHeight(), jobs);
        }

        if (rmlUi == null) return Collections.emptyList();

        boolean escDown = jobs.inputHandler.isKeyDown(GLFW.GLFW_KEY_ESCAPE);
        if (escDown && !escWasDown) {
            jobs.setPaused(!jobs.paused);
            applyPauseState(jobs);
        }
        escWasDown = escDown;

        // ---- Time-of-day controls (- / =) ----------------------------------
        boolean minusDown = jobs.inputHandler.isKeyDown(GLFW.GLFW_KEY_MINUS);
        boolean equalDown = jobs.inputHandler.isKeyDown(GLFW.GLFW_KEY_EQUAL);
        if (minusDown && !minusWasDown) {
            state.timeOfDay = Math.max(0f, Math.min(1f, state.timeOfDay - TIME_STEP));
        }
        if (equalDown && !equalWasDown) {
            state.timeOfDay = Math.max(0f, Math.min(1f, state.timeOfDay + TIME_STEP));
        }
        minusWasDown = minusDown;
        equalWasDown = equalDown;

        if (jobs.paused) {
            forwardUiMouse(jobs, target);
        }

        // Time display — update every frame so it reflects key presses even when paused.
        rmlUi.setModelString("camera", "timeOfDay", formatTime(state.timeOfDay));

        if (deltaTime > 0.0) {
            float instantFps = (float) (1.0 / deltaTime);
            avgFps = (avgFps == 0f) ? instantFps : avgFps * 0.95f + instantFps * 0.05f;
        }
        rmlUi.setModelString("camera", "fps", String.valueOf((int) avgFps));

        Runtime rt  = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb  = rt.maxMemory() / (1024 * 1024);
        rmlUi.setModelString("camera", "mem", usedMb + " / " + maxMb + " MB");

        if (!jobs.paused) {
            rmlUi.setModelFloat("camera", "fov",  jobs.fov);
            rmlUi.setModelFloat("camera", "posX", jobs.position.x());
            rmlUi.setModelFloat("camera", "posY", jobs.position.y());
            rmlUi.setModelFloat("camera", "posZ", jobs.position.z());
        }

        rmlUi.update();
        rmlUi.render();

        return Collections.emptyList();
    }

    private void forwardUiMouse(JobModule jobs, RenderTarget target) {
        int w = target.getWidth();
        int h = target.getHeight();
        int mx = (int) jobs.inputHandler.getXcursor();
        int my = (int) jobs.inputHandler.getYcursor();
        mx = Math.max(0, Math.min(mx, w - 1));
        my = Math.max(0, Math.min(my, h - 1));
        rmlUi.mouseMove(mx, my, 0);

        for (int b = 0; b < 3; b++) {
            boolean down = jobs.inputHandler.isButtonDown(b);
            if (down != prevMouseButtons[b]) {
                if (down) rmlUi.mouseDown(b, 0);
                else      rmlUi.mouseUp(b, 0);
                prevMouseButtons[b] = down;
            }
        }
    }

    private void applyPauseState(JobModule jobs) {
        if (rmlUi == null) return;
        if (jobs.paused) {
            jobs.inputHandler.setCursorMode(GLFW.GLFW_CURSOR_NORMAL);
            if (controlsDoc != 0) rmlUi.hideDocument(controlsDoc);
            if (pauseDoc != 0) rmlUi.showDocument(pauseDoc);
            syncMouseButtons(jobs);
        } else {
            jobs.inputHandler.setCursorMode(GLFW.GLFW_CURSOR_DISABLED);
            if (pauseDoc != 0) rmlUi.hideDocument(pauseDoc);
            if (controlsDoc != 0) rmlUi.showDocument(controlsDoc);
            syncMouseButtons(jobs);
        }
    }

    private void syncMouseButtons(JobModule jobs) {
        for (int i = 0; i < prevMouseButtons.length; i++) {
            prevMouseButtons[i] = jobs.inputHandler.isButtonDown(i);
        }
    }

    private void resumeFromPause() {
        if (jobModuleRef != null) {
            jobModuleRef.setPaused(false);
            escWasDown = jobModuleRef.inputHandler.isKeyDown(GLFW.GLFW_KEY_ESCAPE);
            applyPauseState(jobModuleRef);
        }
    }

    private JobModule jobModuleRef;

    // ---- Helpers -----------------------------------------------------------

    /** Convert a 0–1 time-of-day fraction to a 12-hour clock string, e.g. "6:30 AM". */
    private static String formatTime(float tod) {
        float hours = ((tod % 1f) + 1f) % 1f * 24f;
        int h = (int) hours;
        int m = (int) ((hours - h) * 60);
        String ampm = (h < 12) ? "AM" : "PM";
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        return String.format("%d:%02d %s", h12, m, ampm);
    }

    // ---- GL init (called once on first render frame) -----------------------

    private void init(int width, int height, JobModule jobs) {
        initialized = true;
        jobModuleRef = jobs;

        try {
            rmlUi = new RmlUi(width, height);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[ControlsHudLayer] librmlui_java not found — " +
                               "build native/build.sh and add natives/ to java.library.path");
            rmlUi = null;
            return;
        }

        boolean fontOk = rmlUi.loadFont("res/ui/fonts/Roboto-Regular.ttf");
        if (!fontOk) {
            System.err.println("[ControlsHudLayer] Font not found: res/ui/fonts/Roboto-Regular.ttf");
            System.err.println("  → Download Roboto from fonts.google.com, place in res/ui/fonts/");
        }

        rmlUi.createDataModel("camera",
            new String[]{"fov", "posX", "posY", "posZ"},
            new String[]{"timeOfDay", "fps", "mem"});

        rmlUi.setEventCallback(uiEvents);

        controlsDoc = rmlUi.loadDocument("res/ui/controls.rml");
        pauseDoc    = rmlUi.loadDocument("res/ui/pause.rml");

        if (controlsDoc != 0) rmlUi.showDocument(controlsDoc);
        if (pauseDoc != 0) rmlUi.hideDocument(pauseDoc);

        if (pauseDoc != 0) {
            rmlUi.addEventListener(pauseDoc, "resume-btn", "click");
        }

        jobs.setFramebufferResizeHandler((w, h) -> {
            if (rmlUi != null) {
                rmlUi.setDimensions(w, h);
            }
        });
    }
}

