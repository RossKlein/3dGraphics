package Modules.scene;

import Modules.Job;
import Modules.JobModule;
import Modules.JobQueue;

import java.util.ArrayList;

public interface Scene {
    ArrayList<Job> update(JobModule module);
    ArrayList<Job> render(JobModule module, double deltaTime);
    ArrayList<Job> start(JobModule module, Utils utils);
    String getName();
}