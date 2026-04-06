package Ross.Instance;

import Ross.Modules.JobModule;
import Ross.Modules.scene.LayerManager;

public class Main {

    public static void main(String[] args) {
        JobModule    taskmaster = new JobModule();
        LayerManager lm         = new LayerManager();

        // First v4 entry point: DebugLayer only.
        // World FBO renders black, post-process blits it to screen, then the
        // flamegraph overlay draws on top.  Press ENTER to capture a frame.
        //
        // TODO: add Scenes.loadGameplay(lm, taskmaster, gameState) here once the
        //       first WORLD content layer (sky/terrain) is implemented.
        lm.push(new DebugLayer(), taskmaster);

        taskmaster.start(lm);
    }
}

