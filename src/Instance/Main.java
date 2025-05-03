package Instance;

import Modules.JobModule;
import Modules.scene.Scene;

public class Main {

    public static void main(String[] args) {
        JobModule taskmaster = new JobModule();
        testscene calls = new testscene(taskmaster);
        taskmaster.start(calls);

    }
}
