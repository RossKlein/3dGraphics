package Modules;

import Modules.scene.Utils;

public abstract class JobwithUtils extends Job{

    public Utils utils;

    public JobwithUtils() {
        super();
    }

    public void setUtils(Utils utils) {
        this.utils = utils;
    }
}
