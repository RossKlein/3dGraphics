package Ross.Instance;

import Ross.Modules.*;
import Ross.Modules.flamegraph.FlameRect;
import Ross.Modules.flamegraph.FlamegraphBuilder;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.scene.Scene;
import Ross.Modules.scene.Utils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class testscene implements Scene {

    JobModule module;
    Model floor;
    Utils utils;
    public Mat4f modelm;
    public Model model;
    List<JobProfiler.FlameEvent> events;
    FlamegraphBuilder builder;
    ModelBuilder modelBuilder;
    Model flamegraphModel;
    List<FlameRect> rects;
    boolean enterdown = false;

    public testscene(JobModule module) {

        this.module = module;
        events = new ArrayList<>();
        this.modelBuilder = new ModelBuilder();
    }

    private Job matrixWork = new Job() {
        @Override
        public void code() {


            modelm = modelm.dot(modelm.translation(module.position.x(), module.position.y(), module.position.z()), modelm.dot(module.rotationctrl.toMatrix(), modelm.translation(0,0,0)));
            if(module.inputHandler.isKeyDown(GLFW.GLFW_KEY_ENTER)){
                events = JobProfiler.getFlamegraphEvents();
                builder = new FlamegraphBuilder(events, Settings.width);
                rects = builder.build(events);
                enterdown = true;
            }
            if(module.inputHandler.isKeyDown(GLFW.GLFW_KEY_BACKSPACE)){
                JobProfiler.reset();
                events = new ArrayList<>();
                builder = new FlamegraphBuilder(events, Settings.width);
                rects = builder.build(events);
                enterdown = true;

            }
        }
    };

    @Override
    public ArrayList<Job> update(JobModule module) {
        ArrayList<Job> jobList = new ArrayList<>();


        jobList.add(matrixWork);


        return jobList;
    }


    private Job renderWork = new Job() {
        @Override
        public void code() {

            module.xrotate = (float)(utils.xvel/4);
            module.yrotate = (float)(utils.yvel/4);
            module.fov = (float)(utils.fov);

        }
    };

    @Override
    public ArrayList<Job> render(JobModule module, double deltaTime) {

        module.renderer.loadMatrix(module.modelview, "v", false, 1);
        module.renderer.loadMatrix(module.perspective, "p", false, 2);
        module.renderer.loadLightSource(new Vec3f(-200, 200, 300));
        module.renderer.loadMatrix(modelm, "m", false, 0);
        module.renderer.addBindBool("useFlatColor", false);

        module.renderer.renderModel(model);

        // 2. Render Flamegraph Overlay

        // Create orthographic projection matrix
        Mat4f ortho = new Mat4f().orthographic(0, Settings.width, Settings.height, 0, -1, 1);

        module.renderer.loadMatrix(new Mat4f().identity(), "m", false, 0);
        module.renderer.loadMatrix(new Mat4f().identity(), "v", false, 1);
        module.renderer.loadMatrix(ortho, "p", false, 2);
        module.renderer.loadLightSource(new Vec3f(0, 0, 1)); // flamegraph is flat
        module.renderer.addBindBool("useFlatColor", true);
        // Get flamegraph events

        if (enterdown == true && rects != null){

            // Build and render
            if(flamegraphModel != null) {
                flamegraphModel.dispose();
            }
            flamegraphModel = FlameRect.buildFlamegraphModel(rects, modelBuilder);
            enterdown = false;
        };
        if(flamegraphModel != null){

            module.renderer.renderModel(flamegraphModel);
        }

        ArrayList<Job> jobList = new ArrayList<>();
        jobList.add(renderWork);


        return jobList;
    }

    private Job startWork = new Job() {
        @Override
        public void code() {
            modelm = new Mat4f().identity();


        }
    };
    @Override
    public ArrayList<Job> start(JobModule module, Utils utils) {
        this.utils = utils;
        ArrayList<Job> jobList = new ArrayList<>();
        //set utils for all util necessary jobs
        model = this.utils.buildModel("res/test/utah-teapot.obj");

        jobList.add(startWork);

        return jobList;
    }

    @Override
    public String getName() {
        return null;
    }
}
