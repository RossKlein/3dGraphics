package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.models.Model;
import Ross.Modules.models.OBJloader;
import Ross.Modules.models.OBJobject;
import Ross.Modules.models.ModelBuilder;
import Ross.Modules.math.Vec4f;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.RenderTarget;

import java.util.Collections;
import java.util.List;

/**
 * First concrete WORLD layer — renders the teapot test model.
 *
 * Exercises the full pipeline:
 *   onPush()  → loads teapot OBJ
 *   update()  → recomputes model matrix from camera position + rotation
 *   render()  → mirrors mouse input to JobModule rotate fields, draws teapot
 *
 * Camera controls live in JobModule.controls (WASD / mouse / scroll).
 * This layer bridges the mouse-delta → xrotate/yrotate copy that was
 * previously done by testscene's renderWork job.
 */
public class TestWorldLayer extends BaseLayer {

    private JobModule jobs;
    private Model     teapot;

    // Model matrix — written by update job, read by render() on GL thread.
    private volatile Mat4f modelMatrix = new Mat4f().identity();

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.WORLD; }
    @Override public int    getOrder() { return 1; }
    @Override public String getName()  { return "test-world"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override
    public List<Job> onPush(JobModule jobs) {
        this.jobs = jobs;

        // Load synchronously — onPush() is called before the render loop starts,
        // so this runs on the main thread. OBJloader is CPU-only (no GL calls).
        OBJloader  obj      = new OBJloader("res/test/utah-teapot.obj");
        OBJobject  objModel = obj.returnOBJobject();
        ModelBuilder builder = new ModelBuilder();

        float[] verts   = objModel.getVertices();
        int[]   indices = objModel.getIndices();
        float[] colors  = obj.genColor(new Vec4f(0.2f, 0.7f, 0.3f, 1f)); // green teapot
        float[] normals = objModel.getNormals();

        teapot = builder.buildModel(verts, indices, colors, normals);
        return Collections.emptyList();
    }

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (teapot != null) { teapot.dispose(); teapot = null; }
        return Collections.emptyList();
    }

    // ---- Update (non-GL, thread pool) --------------------------------------

    private final Job matrixJob = new Job() {
        @Override
        public void code() {
            // Recompute model matrix: translate by camera position, apply rotation.
            // This is the same pattern as the old testscene matrixWork job.
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
        if (teapot == null) return Collections.emptyList();

        // Copy mouse delta → rotation inputs consumed by JobModule.controls.
        // These are volatile writes (render thread) read by controls (update thread).
        if (jobs.getUtils() != null) {
            jobs.xrotate = (float) (jobs.getUtils().xvel / 4.0);
            jobs.yrotate = (float) (jobs.getUtils().yvel / 4.0);
            jobs.fov     = (float)  jobs.getUtils().fov;
        }

        // Draw the teapot
        jobs.renderer.loadMatrix(jobs.modelview,  "v", false, 1);
        jobs.renderer.loadMatrix(jobs.perspective, "p", false, 2);
        jobs.renderer.loadMatrix(modelMatrix,      "m", false, 0);
        jobs.renderer.loadLightSource(new Vec3f(-200, 200, 300));
        jobs.renderer.addBindBool("useFlatColor", false);
        jobs.renderer.renderModel(teapot);

        return Collections.emptyList();
    }
}
