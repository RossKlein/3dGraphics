package Ross.Instance;

import Ross.Modules.JobModule;
import Ross.Modules.scene.Scene;

public class Main {

    public static void main(String[] args) {
        JobModule taskmaster = new JobModule();
        testscene calls = new testscene(taskmaster);
        taskmaster.start(calls);

    }
}
