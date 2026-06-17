package Ross.Modules;

import Ross.Modules.input.InputHandler;
import Ross.Modules.math.*;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.models.OBJloader;
import Ross.Modules.models.OBJobject;
import Ross.Modules.scene.Scene;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.CallbackI;
import Ross.Modules.scene.LayerManager;
import Ross.Modules.scene.Utils;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class JobModule {

    public Window window;
    public Engine engine;
    public JobQueue updateQueue;
    public JobQueue renderQueue;
    public InputHandler inputHandler;
    public Time time;
    public static int N_THREADS    = 4;
    /** Background-job pool size: one fewer thread than FG so FG always has capacity. */
    public static int N_BG_THREADS = Math.max(1, N_THREADS - 1);
    public ExecutorService pool   = null;
    /** Separate pool for background (BG) jobs so they cannot starve FG jobs. */
    public ExecutorService bgPool = null;
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

    Scene        currentScene  = null;
    LayerManager layerManager = null;

    /** When true, gameplay movement (controls job) and camera mouse look are frozen; UI may use mouse. */
    public volatile boolean paused = false;

    /** Optional: e.g. RmlUi {@code setDimensions} when the framebuffer size changes (GL thread). */
    private BiConsumer<Integer, Integer> framebufferResizeHandler;

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void setFramebufferResizeHandler(BiConsumer<Integer, Integer> handler) {
        this.framebufferResizeHandler = handler;
    }

    /**
     * Called from the GLFW framebuffer-size callback (typically during {@code glfwPollEvents}
     * on the render thread). Updates {@link LayerManager} offscreen targets and notifies UI.
     */
    public void onFramebufferResized(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (layerManager != null) {
            layerManager.resize(width, height);
        }
        if (framebufferResizeHandler != null) {
            framebufferResizeHandler.accept(width, height);
        }
    }

    /**
     * GL-thread job queue.
     *
     * Worker threads post jobs here (via a captured {@code jobs} reference) when
     * they need follow-up work on the GL thread — typically mesh/texture uploads.
     * Jobs with {@link Job#glThread} = true that pass through {@link #updateJobs}
     * are also routed here automatically.
     *
     * Drained synchronously at the start of each render frame, before any layer
     * renders, so uploaded resources are available in the same frame.
     *
     * Thread-safe: multiple worker threads may enqueue concurrently.
     */
    public final ConcurrentLinkedQueue<Job> glJobs = new ConcurrentLinkedQueue<>();

    public Renderer renderer;

    public JobModule() {
        time = new Time();
        window = new Window(this);
        window.setSize(Settings.width, Settings.height);
        engine = new Engine(this);
        inputHandler = new InputHandler();
        updateQueue = new JobQueue();
        renderQueue = new JobQueue();
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

        JobQueue startQueue = new JobQueue();
        for (Job getjob : currentScene.start(this, utils)) {
            startQueue.assign(getjob);
        }
        LinkedList<Job> queue = startQueue.frameCall();

        int poolCount = 0;
        for (Job job : queue) {
            if (job.glThread) {
                job.asRunnable(startQueue).run(); // run on main thread — GL context is live
            } else {
                poolCount++;
            }
        }

        if (poolCount > 0) {
            AtomicInteger jobsInFlight = new AtomicInteger(poolCount);
            CountDownLatch latch = new CountDownLatch(1);
            for (Job job : queue) {
                if (job.glThread) continue;
                pool.submit(() -> {
                    try {
                        job.asRunnable(startQueue).run();
                    } finally {
                        if (jobsInFlight.decrementAndGet() == 0) latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        //last thing to do
        engine.renderLoop(this);

    }

    /**
     * Start the engine using the v4 Layer system.
     *
     * Push your initial layers via {@link Ross.Modules.scene.Scenes} before calling this:
     * <pre>{@code
     * LayerManager lm = new LayerManager();
     * GameState    gs = new GameState();
     * Scenes.loadMainMenu(lm, taskmaster);
     * taskmaster.start(lm);
     * }</pre>
     */
    public void start(LayerManager lm) {
        this.layerManager = lm;

        window.create(0);
        inputHandler.registerInputHandler(window.getWindowId());
        inputHandler.setCursorPos(0, 0);
        renderer = new Renderer();
        utils.setInputHandler(inputHandler);
        utils.fov = 70;
        fov = 70;  // default so matrixWork projection is valid from frame 1

        this.pool   = engine.getPool(N_THREADS);
        this.bgPool = Executors.newFixedThreadPool(N_BG_THREADS, r -> {
            Thread t = new Thread(r);
            t.setName("bgWorker-" + t.getId());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });

        // Run all CPU-only onPush() jobs (file I/O, data parsing) and wait for
        // them to finish before entering the render loop.  These jobs were
        // returned by lm.push() calls in Main and collected by LayerManager.
        // GL uploads happen lazily in each layer's first render() call instead.
        LinkedList<Job> startJobs = lm.drainPushJobs();
        if (!startJobs.isEmpty()) {
            JobQueue startQueue = new JobQueue();
            // GL-pinned start jobs are unusual but supported — run them here on
            // the main thread before the render loop begins (GL context is live).
            int poolCount = 0;
            for (Job job : startJobs) {
                if (job.glThread) {
                    job.asRunnable(startQueue).run();
                } else {
                    poolCount++;
                }
            }
            if (poolCount > 0) {
                AtomicInteger inFlight = new AtomicInteger(poolCount);
                CountDownLatch latch   = new CountDownLatch(1);
                for (Job job : startJobs) {
                    if (job.glThread) continue;
                    pool.submit(() -> {
                        try {
                            job.asRunnable(startQueue).run();
                        } finally {
                            if (inFlight.decrementAndGet() == 0) latch.countDown();
                        }
                    });
                }
                try { latch.await(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
        }

        Thread updateThread = new Thread(engine.updateLoop(this));
        updateThread.setName("updateThread");
        updateThread.setDaemon(true);

        engine.run = true;
        updateThread.start();

        window.setVisible(true);

        engine.renderLoop(this);
    }



    private Job controls = new Job() {
        { name = "controls"; }

        @Override
        public void code() {
            if (paused) {
                return;
            }

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
            float scale = 0.5f;
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
        { name = "matrixWork"; }

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

        if (layerManager != null) {
            for (Job job : layerManager.collectUpdateJobs(this))
                updateQueue.assign(job);
        } else if (currentScene != null) {
            for (Job job : currentScene.update(this))
                updateQueue.assign(job);
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

        // Separate GL-pinned jobs and background jobs.
        // GL jobs  → glJobs queue (run on render thread, not counted in latch).
        // Background jobs → pool (fire-and-forget, not counted in latch).
        // Foreground jobs → pool AND counted in latch (update loop blocks for these).
        int poolJobCount = 0;
        for (Job job : queue) {
            if (job.glThread) {
                job.initTime = System.nanoTime();  // stamp GL-queue entry time for accurate wait_ms
                glJobs.add(job);
            } else if (!job.background) {
                poolJobCount++;   // only foreground pool jobs block the tick
            }
        }

        // Submit ALL non-GL jobs to the appropriate pool:
        //   BG jobs → bgPool  (dedicated pool, cannot starve FG workers)
        //   FG jobs → pool    (counted in latch, update loop waits for these)
        if (poolJobCount == 0) {
            for (Job job : queue) {
                if (!job.glThread) {
                    bgPool.submit(() -> job.asRunnable(updateQueue).run());
                }
            }
            return;
        }

        AtomicInteger jobsInFlight = new AtomicInteger(poolJobCount);
        CountDownLatch latch = new CountDownLatch(1);

        for (Job job : queue) {
            if (job.glThread) continue;
            if (job.background) {
                // Fire and forget on the BG pool — FG pool stays free for controls/matrix.
                bgPool.submit(() -> job.asRunnable(updateQueue).run());
            } else {
                pool.submit(() -> {
                    try {
                        job.asRunnable(updateQueue).run();
                    } finally {
                        if (jobsInFlight.decrementAndGet() == 0) {
                            latch.countDown();
                        }
                    }
                });
            }
        }

        latch.await();


    }

    /**
     * Maximum milliseconds to spend draining GL-upload jobs per frame.
     * Keeps frame hitches bounded even when many chunks finish simultaneously.
     * At 100 FPS the total frame budget is 10 ms; we reserve 2 ms for uploads
     * so the scene still renders at full rate during heavy loading.
     */
    public static final double GL_UPLOAD_BUDGET_MS = 2.0;

    public void renderJobs(double deltaTime) {
        JobProfiler.markFrame("FRAME START", System.nanoTime(), "Frame");

        // Drain GL-pinned jobs up to the time budget.
        // This prevents a burst of pending uploads from stalling the render thread
        // for an entire frame (e.g. 4 × 8ms VAO uploads = 32ms).
        long uploadStart = System.nanoTime();
        Job glJob;
        while ((glJob = glJobs.poll()) != null) {
            if (!glJob.isCancelled()) {
                glJob.asRunnable(updateQueue).run();
            }
            double elapsedMs = (System.nanoTime() - uploadStart) / 1_000_000.0;
            if (elapsedMs >= GL_UPLOAD_BUDGET_MS) break;
        }

        utils.setGameInputEnabled(!paused);
        utils.updateInput();





        if (layerManager != null) {
            for (Job job : layerManager.renderFrame(this, deltaTime))
                renderQueue.assign(job);
        } else if (currentScene != null) {
            for (Job job : currentScene.render(this, deltaTime))
                renderQueue.assign(job);
        }
        LinkedList<Job> queue = renderQueue.frameCall();
        for (Job job : queue) {
            job.asRunnable(renderQueue).run();
        }
        //all gpu Scene happen in render tasks
        //render tasks has gpu context

        //matrix loading should take place in render
        //render models call
        //there should be a list of models in the scene which need to be rendered and are batch called
        JobProfiler.markFrame("FRAME END", System.nanoTime(), "Frame");

    }



    public Utils getUtils() {
        return utils;
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
