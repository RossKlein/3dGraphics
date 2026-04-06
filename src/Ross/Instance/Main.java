package Ross.Instance;

import Ross.Modules.JobModule;
import Ross.Modules.scene.LayerManager;

public class Main {

    public static void main(String[] args) {
        JobModule    taskmaster = new JobModule();
        LayerManager lm         = new LayerManager();

        // Test scene — exercises the full v4 layer pipeline:
        //
        //   TestWorldLayer   WORLD  1   teapot in 3D, WASD camera
        //   ControlsHudLayer SCREEN 0   RmlUi key reference + live FOV/position
        //   DebugLayer       SCREEN 99  flamegraph profiler overlay
        //
        // Controls:
        //   W/A/S/D        move         Q/E          up/down
        //   Mouse drag     rotate       Ctrl+drag    slow rotate
        //   Scroll         FOV zoom
        //   Enter          capture flamegraph
        //   Backspace      reset flamegraph
        //
        // Font setup (required for ControlsHudLayer text):
        //   Place Roboto-Regular.ttf in res/ui/fonts/  (see README.txt there)
        //   Build the native lib: cd native && ./build.sh

        lm.push(new TestWorldLayer(),   taskmaster);
        lm.push(new ControlsHudLayer(), taskmaster);
        lm.push(new DebugLayer(),       taskmaster);

        taskmaster.start(lm);
    }
}
