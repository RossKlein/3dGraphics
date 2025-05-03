package Modules;

import Modules.input.InputHandler;
import Modules.math.*;
import Modules.models.Model;
import Modules.models.ModelBuilder;
import Modules.models.OBJloader;
import Modules.models.OBJobject;
import Modules.scene.Scene;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.CallbackI;
import Modules.scene.Utils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JobModule {

    public Window window;
    public Engine engine;
    public JobQueue updateQueue;
    public JobQueue renderQueue;
    public InputHandler inputHandler;
    public Time time;
    public static int N_THREADS = 4;
    public ExecutorService pool = null;
    volatile public Mat4f modelview;
    volatile public Mat4f perspective;
    private Vec3f target;
    private Vec3f zdir;
    private Camera camera;
    private Quaternion rotation;
    private Utils utils;
    Vec3f zero_one_zero;
    Vec3f none_zero_zero;

    //things that are shared across threads and used in jobs
    volatile public Quaternion rotationctrl;
    volatile public float fov;
    volatile public float xrotate;
    volatile public float yrotate;
    volatile public float zrotate;
    volatile public Vec3f position = new Vec3f(0, 0, -30);

    Scene currentScene = null;

    //test
    public Renderer renderer;
    //etc.

    public JobModule() {
        time = new Time();
        window = new Window(this);
        window.setSize(Settings.width, Settings.height);
        engine = new Engine(this);
        inputHandler = new InputHandler();
        updateQueue = new JobQueue(time);
        renderQueue = new JobQueue(time);
        camera = new Camera();
        camera.rotate(0, 0, 0);
        target = zdir =  new Vec3f(0, 0, -1);
        rotation = new Quaternion();
        rotationctrl = new Quaternion();
        utils = new Utils();
        modelview = perspective = new Mat4f().identity();
        zero_one_zero = new Vec3f(0, 1, 0);
        none_zero_zero = new Vec3f(-1, 0, 0);

    }
    public void start(Scene currentscene) {



        window.create(0);
        inputHandler.registerInputHandler( window.getWindowId());
        inputHandler.setCursorPos(0,0);
        renderer = new Renderer();
        utils.setInputHandler(inputHandler);
        utils.fov = 70;

        this.currentScene = currentscene;
        this.pool = engine.getPool(N_THREADS);
        Thread updateThread = new Thread(engine.updateLoop(this));
        updateThread.setName("updateThread");
        updateThread.setDaemon(true);

        engine.run = true;
        updateThread.start();

        window.setVisible(true);


        //test

        //run currentscene start
        JobQueue startQueue = new JobQueue(time);

        for (Job getjob : currentScene.start(this, utils)) {
            startQueue.assign(getjob);
        }
        LinkedList<Job> queue = startQueue.frameCall();
        AtomicInteger jobsInFlight = new AtomicInteger(queue.size());
        CountDownLatch latch = new CountDownLatch(1);

        for (Job job : queue) {
            pool.submit(() -> {
                try {
                    job.asRunnable(startQueue).run();
                } finally {
                    if (jobsInFlight.decrementAndGet() == 0) {
                        latch.countDown(); // All jobs (and subtasks) complete
                    }
                }
            });
        }


        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        //last thing to do
        engine.renderLoop(this);

    }



    private Job controls = new Job() {

        @Override
        public void code() {

            zrotate /= 2;
            yrotate /= 2;
            xrotate /= 2;

            if(inputHandler.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL)) { //ctrl

                rotationctrl = rotationctrl.mult(rotationctrl,rotationctrl.rotation(1, 0, 0, -0.2f * yrotate) );
                rotationctrl = rotationctrl.mult(rotationctrl.rotation(0, 1, 0, -0.2f * xrotate), rotationctrl);


            } else {

                Quaternion y = rotationctrl.rotation(1, 0, 0, -0.4f * yrotate);
                Quaternion x = rotationctrl.rotation(0, 1, 0, -0.4f * xrotate);


                Vec3f yDir = rotation.rotate(rotation, zero_one_zero);
                if(yDir.dot(new Vec3f(0, 1, 0), yDir) < 0){
                    x = rotationctrl.rotation(0, -1, 0, -0.4f * xrotate);

                }
                Quaternion z = rotationctrl.rotation(0, 0, 1, -0.4f * zrotate).conjugate();
                rotation = rotation.mult(rotation, z);
                rotation = rotation.mult(rotation, y);
                rotation = rotation.mult(x, rotation);
            }

            Vec3f forward = rotation.rotate(rotation, zdir);
            Vec3f xDir = rotation.rotate(rotation, none_zero_zero);
            Vec3f yDir = rotation.rotate(rotation, zero_one_zero);
            float scale = 0.2f;
            forward = forward.mult(scale);
            xDir = xDir.mult(scale);
            yDir = yDir.mult(scale);


            if (inputHandler.isKeyDown(87)) { //w

                position = position.sub(position, forward);
            }
            if (inputHandler.isKeyDown(83)) {//s
                position = position.add(position, forward);

            }
            if (inputHandler.isKeyDown(65)) {//a
                position = position.sub(position, xDir);
            }
            if (inputHandler.isKeyDown(68)) {//d
                position = position.add(position, xDir);

            }
            if (inputHandler.isKeyDown(81)) {//q
                position = position.sub(position, yDir);
            }
            if (inputHandler.isKeyDown(69)) {//e
                position = position.add(position, yDir);

            }


        }
    };
    private Job matrixWork = new Job() {
        @Override
        public void code() {

            perspective = perspective.projection(fov, (float) Settings.width/(float)Settings.height, 0.01f, -1000);

            modelview = rotation.toMatrix();


        }
    };

    //Main thread - render thread
    //Update thread - dispatches each update frames Scene to the threadpool - tick timed
    //thread pool

    /*
    currently implemented using java job stealing pool
    updatequeue.framecall organizes the jobs into a final priority queue

    the queue is looped through until all jobs have been executed
    the pool automatically distributes the jobs across the threads

     */
    public void updateJobs(double deltaTime) throws ExecutionException, InterruptedException {



        updateQueue.assign(controls);
        updateQueue.assign(matrixWork);


        for (Job getjob : currentScene.update(this)) {
            updateQueue.assign(getjob);
        }
        //first add all tasks to queue, then execute framecall

        //camera should be handled independently of scenes
        //input should be handled independently of scenes
        //conditional model building?
        //maybe have a list of update tasks with some built in and others can be appended

        //example of initializer tasks to be handled
        //model loading
        //texture loading
        //model building
        //create matrices

        //example of update tasks to be handled    //these tasks get update priority because they are per frame
        //game logic handling
        //input handling
        //velocity and matrix calculations based on input

        LinkedList<Job> queue = updateQueue.frameCall();
        AtomicInteger jobsInFlight = new AtomicInteger(queue.size());
        CountDownLatch latch = new CountDownLatch(1);

        for (Job job : queue) {
            pool.submit(() -> {
                try {
                    job.asRunnable(updateQueue).run();
                } finally {
                    if (jobsInFlight.decrementAndGet() == 0) {
                        latch.countDown(); // All jobs (and subtasks) complete
                    }
                }
            });
        }

        latch.await();


    }

    //what tasks should be update tasks and what tasks should be render tasks
    //are render tasks serial while update tasks are parallel?
    public void renderJobs(double deltaTime) {
        JobProfiler.markFrame("FRAME START", System.nanoTime(), "Frame");


        utils.updateInput();





        for (Job getjob : currentScene.render(this, deltaTime)) {
            renderQueue.assign(getjob);
        }
        LinkedList<Job> queue = renderQueue.frameCall();
        AtomicInteger jobsInFlight = new AtomicInteger(queue.size());
        CountDownLatch latch = new CountDownLatch(1);

        for (Job job : queue) {
            pool.submit(() -> {
                try {
                    job.asRunnable(renderQueue).run();
                } finally {
                    if (jobsInFlight.decrementAndGet() == 0) {
                        latch.countDown(); // All jobs (and subtasks) complete
                    }
                }
            });
        }


        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //all gpu Scene happen in render tasks
        //render tasks has gpu context

        //matrix loading should take place in render
        //render models call
        //there should be a list of models in the scene which need to be rendered and are batch called
        JobProfiler.markFrame("FRAME END", System.nanoTime(), "Frame");

    }



    public Window getWindow() {
        return window;
    }

    public void setWindow(Window window) {
        this.window = window;
    }

    public Engine getEngine() {
        return engine;
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }



}
