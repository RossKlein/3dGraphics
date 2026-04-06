package Ross.Instance;

import Ross.Modules.JobModule;
import Ross.Modules.scene.GameState;
import Ross.Modules.scene.LayerManager;

public class Main {

    public static void main(String[] args) {
        JobModule    taskmaster = new JobModule();
        LayerManager lm         = new LayerManager();
        GameState    state      = new GameState();

        // Scene: sky background + teapot + UI overlays
        //
        // WORLD layers (render into offscreen FBO → post-process → screen):
        //   SkyLayer         order 0   procedural sky gradient + sun disc + halo
        //   TestWorldLayer   order 1   teapot in 3-D, WASD/mouse camera
        //
        // Post-process (added by SkyLayer on first render):
        //   GodRaysPass              64-sample radial blur toward the sun
        //
        // SCREEN layers (drawn on top of the post-processed image):
        //   ControlsHudLayer order 0  RmlUi key reference + live FOV/position
        //   DebugLayer       order 99 flamegraph profiler overlay
        //
        // Controls:
        //   W/A/S/D        move         Q/E          up/down
        //   Mouse drag     rotate       Ctrl+drag    slow rotate
        //   Scroll         FOV zoom
        //   Esc            toggle pause menu
        //   Enter          capture flamegraph
        //   Backspace      reset flamegraph
        //
        // Font setup (required for ControlsHudLayer text):
        //   Place Roboto-Regular.ttf in res/ui/fonts/  (see README.txt there)
        //   Build the native lib: cd native && ./build.sh
        //
        // Time of day:
        //   state.timeOfDay drives sky colour and sun position.
        //   0.25 = dawn, 0.5 = noon, 0.75 = dusk.  Tweak here to preview.
        state.timeOfDay = 0.45f;   // just before noon — strong sun + visible god rays

        lm.push(new SkyLayer(state, lm), taskmaster);
        lm.push(new TestWorldLayer(),    taskmaster);
        lm.push(new ControlsHudLayer(),  taskmaster);
        lm.push(new DebugLayer(),        taskmaster);

        taskmaster.start(lm);
    }
}
