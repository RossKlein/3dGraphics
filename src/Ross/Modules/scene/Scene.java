package Ross.Modules.scene;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.JobQueue;

import java.util.ArrayList;

public interface Scene {
    ArrayList<Job> update(JobModule module);
    ArrayList<Job> render(JobModule module, double deltaTime);
    ArrayList<Job> start(JobModule module, Utils utils);
    String getName();
}