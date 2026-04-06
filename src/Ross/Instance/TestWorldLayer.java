package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.math.Vec4f;
import Ross.Modules.models.Model;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.models.OBJloader;
import Ross.Modules.models.OBJobject;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.RenderTarget;

import java.util.Collections;
import java.util.List;

/**
 * First concrete WORLD layer — renders the teapot test model.
 *
 * Loading is split across two phases to respect the GL context rule:
 *
 *   onPush()  → returns a job that parses the OBJ on a worker thread (CPU only)
 *   render()  → on the first call, uploads parsed data to the GPU (GL thread)
 *               subsequent calls just draw
 *
 * This is the required pattern for all layers that own GL resources.
 * Never call glGen*/glBuffer*/etc. in onPush() or update() — the GL context
 * does not exist when those run.
 */
public class TestWorldLayer extends BaseLayer {

    private JobModule jobs;

    // ---- CPU data (written by load job, consumed by first render) ----------
    // volatile: load job (worker thread) writes, render() (GL thread) reads.
    private volatile float[] rawVerts;
    private volatile int[]   rawIndices;
    private volatile float[] rawColors;
    private volatile float[] rawNormals;

    // ---- GL resources (created on GL thread in first render()) -------------
    private Model teapot     = null;
    private boolean glReady  = false;

    // Model matrix — written by update job, read by render() on GL thread.
    private volatile Mat4f modelMatrix = new Mat4f().identity();

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.WORLD; }
    @Override public int    getOrder() { return 1; }
    @Override public String getName()  { return "test-world"; }

    // ---- Lifecycle ---------------------------------------------------------

    /**
     * Store the JobModule reference and kick off CPU-only OBJ parsing.
     * No GL calls — context doesn't exist yet.
     */
    @Override
    public List<Job> onPush(JobModule jobs) {
        this.jobs = jobs;
        return List.of(new Job() {
            @Override
            public void code() {
                OBJloader obj      = new OBJloader("res/test/utah-teapot.obj");
                OBJobject objModel = obj.returnOBJobject();
                rawVerts   = objModel.getVertices();
                rawIndices = objModel.getIndices();
                rawColors  = obj.genColor(new Vec4f(0.2f, 0.7f, 0.3f, 1f));
                rawNormals = objModel.getNormals();
                // volatile write — render() on the GL thread will see this
            }
        });
    }

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (teapot != null) { teapot.dispose(); teapot = null; }
        rawVerts = null;
        rawIndices = null;
        rawColors = null;
        rawNormals = null;
        glReady = false;
        return Collections.emptyList();
    }

    // ---- Update (non-GL, thread pool) --------------------------------------

    private final Job matrixJob = new Job() {
        @Override
        public void code() {
            Mat4f t = new Mat4f().translation(
                jobs.position.x(), jobs.position.y(), jobs.position.z());
            Mat4f r = jobs.rotationctrl.toMatrix();
            modelMatrix = t.dot(t, r);
        }
    };

    @Override
    public List<Job> update(JobModule jobs) {
        return List.of(matrixJob);
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {

        // Lazy GL upload — runs once, on the GL thread, when CPU data is ready.
        if (!glReady && rawVerts != null) {
            teapot  = new ModelBuilder().buildModel(rawVerts, rawIndices, rawColors, rawNormals);
            rawVerts = rawColors = rawNormals = null; // release CPU copies
            rawIndices = null;
            glReady = true;
        }

        if (!glReady) return Collections.emptyList(); // still parsing

        // Mirror mouse delta → rotation inputs read by JobModule.controls.
        if (jobs.getUtils() != null) {
            jobs.xrotate = (float) (jobs.getUtils().xvel / 4.0);
            jobs.yrotate = (float) (jobs.getUtils().yvel / 4.0);
            jobs.fov     = (float)  jobs.getUtils().fov;
        }

        jobs.renderer.loadMatrix(jobs.modelview,   "v", false, 1);
        jobs.renderer.loadMatrix(jobs.perspective,  "p", false, 2);
        jobs.renderer.loadMatrix(modelMatrix,       "m", false, 0);
        jobs.renderer.loadLightSource(new Vec3f(-200, 200, 300));
        jobs.renderer.addBindBool("useFlatColor", false);
        jobs.renderer.renderModel(teapot);

        return Collections.emptyList();
    }
}
