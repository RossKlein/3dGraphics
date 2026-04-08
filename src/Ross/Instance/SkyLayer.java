package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.math.Vec4f;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.FullscreenQuad;
import Ross.Modules.scene.GameState;
import Ross.Modules.scene.GodRaysPass;
import Ross.Modules.scene.LayerManager;
import Ross.Modules.scene.RenderTarget;
import Ross.Modules.shaders.SkyShader;

import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Renders a procedural sky as the first (background) WORLD layer.
 *
 * Pipeline:
 *   1. Draws a fullscreen quad at max depth with depth writes OFF, so all
 *      subsequent geometry in the same WORLD group naturally composites over it.
 *   2. Reconstructs per-pixel view ray directions from NDC via inverse
 *      projection/view, producing a view-direction-correct sky gradient.
 *   3. Adds a sun disc + halo whose position tracks {@code state.timeOfDay}.
 *   4. Computes the sun's screen-space UV position and writes it to
 *      {@code state.sunScreenX / sunScreenY / sunIntensity} for
 *      {@link GodRaysPass} to consume in the same frame.
 *
 * On the first {@link #render} call, lazily initialises GL resources AND
 * registers a {@link GodRaysPass} into the supplied {@link LayerManager}.
 */
public class SkyLayer extends BaseLayer {

    private final GameState    state;
    private final LayerManager layerManager;

    private SkyShader      shader;
    private FullscreenQuad quad;
    private boolean        glReady = false;

    public SkyLayer(GameState state, LayerManager layerManager) {
        this.state        = state;
        this.layerManager = layerManager;
    }

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.WORLD; }
    @Override public int    getOrder() { return 0; }
    @Override public String getName()  { return "sky"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override public List<Job> onPush(JobModule jobs) { return Collections.emptyList(); }

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (glReady) {
            shader.exit();
            quad.dispose();
        }
        return Collections.emptyList();
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {
        if (!glReady) initGL();

        // ---- 1. Compute sun world direction and colour from time of day ----
        float angle  = (float) ((state.timeOfDay - 0.5f) * 2.0 * Math.PI);
        Vec3f sunDir = new Vec3f((float) Math.sin(angle), (float) Math.cos(angle), 0.3f).normalize();

        // Mirror the sky fragment shader's sunCol = mix(orange, white-yellow, dayF).
        float tod  = state.timeOfDay;
        float dayF = Math.max(0f, smoothstep(0.30f, 0.48f, tod) - smoothstep(0.52f, 0.70f, tod));
        state.sunColorR = 1.0f;
        state.sunColorG = 0.50f + dayF * (0.97f - 0.50f);
        state.sunColorB = 0.15f + dayF * (0.80f - 0.15f);

        // ---- 2. Compute sun screen-space UV for GodRaysPass ----------------
        // w=0 so the multiply ignores translation and acts as a 3x3 rotation.
        Mat4f R       = jobs.modelview;
        Vec4f viewDir = R.matrixVecMult(R, new Vec4f(sunDir.x, sunDir.y, sunDir.z, 0f));

        boolean sunInFront = viewDir.z < -0.001f;
        if (sunInFront) {
            float invNegVz   = 1.0f / (-viewDir.z);
            float ndcX       = jobs.perspective.m00() * viewDir.x * invNegVz;
            float ndcY       = jobs.perspective.m11() * viewDir.y * invNegVz;
            state.sunScreenX = ndcX * 0.5f + 0.5f;
            state.sunScreenY = ndcY * 0.5f + 0.5f;
            state.sunIntensity = Math.max(0f, sunDir.y);
        } else {
            state.sunScreenX   = -1f;
            state.sunScreenY   = -1f;
            state.sunIntensity = 0f;
        }

        // ---- 3. Draw sky quad ----------------------------------------------
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        shader.startShader();

        float invFovX = (jobs.perspective.m00() != 0f) ? 1f / jobs.perspective.m00() : 1f;
        float invFovY = (jobs.perspective.m11() != 0f) ? 1f / jobs.perspective.m11() : 1f;
        shader.loadInvFov(invFovX, invFovY);
        shader.loadTimeOfDay(state.timeOfDay);
        shader.loadSunDir(sunDir);

        // Upload column-major 3x3 rotation part of the view matrix.
        float[] rot3 = {
            R.m00(), R.m10(), R.m20(),   // col 0
            R.m01(), R.m11(), R.m21(),   // col 1
            R.m02(), R.m12(), R.m22()    // col 2
        };
        shader.loadViewRot(rot3);

        quad.draw();

        shader.stopShader();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);

        return Collections.emptyList();
    }

    // ---- Helpers -----------------------------------------------------------

    /** GLSL-compatible smoothstep. */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    // ---- Lazy GL init (called once on the GL thread) -----------------------

    private void initGL() {
        shader = new SkyShader();
        quad   = new FullscreenQuad();

        layerManager.getPostProcessChain().addPass(new GodRaysPass(state));

        glReady = true;
    }
}
