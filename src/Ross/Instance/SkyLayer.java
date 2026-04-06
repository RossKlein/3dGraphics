package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.GameState;
import Ross.Modules.scene.GodRaysPass;
import Ross.Modules.scene.LayerManager;
import Ross.Modules.scene.RenderTarget;

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
 *
 * Constructor parameters:
 *   state        — shared game state (timeOfDay drives sun motion + colors)
 *   layerManager — needed only to register the GodRaysPass; stored briefly
 */
public class SkyLayer extends BaseLayer {

    private final GameState    state;
    private final LayerManager layerManager;

    private int     shader;
    private int     vao;
    private boolean glReady = false;

    // Uniform locations
    private int uInvFovX;
    private int uInvFovY;
    private int uViewRot;
    private int uSunDir;
    private int uTimeOfDay;

    public SkyLayer(GameState state, LayerManager layerManager) {
        this.state        = state;
        this.layerManager = layerManager;
    }

    // ---- Layer identity ----------------------------------------------------

    @Override public Group  getGroup() { return Group.WORLD; }
    @Override public int    getOrder() { return 0; }            // behind everything
    @Override public String getName()  { return "sky"; }

    // ---- Lifecycle ---------------------------------------------------------

    @Override public List<Job> onPush(JobModule jobs) { return Collections.emptyList(); }

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (glReady) {
            glDeleteProgram(shader);
            glDeleteVertexArrays(vao);
        }
        return Collections.emptyList();
    }

    // ---- Render (GL thread) ------------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double deltaTime) {
        if (!glReady) initGL();

        // ---- 1. Compute sun world direction from time of day ---------------
        // angle=0 → sun at top (noon), angle=±π/2 → horizon (dawn/dusk)
        float angle   = (float) ((state.timeOfDay - 0.5f) * 2.0 * Math.PI);
        Vec3f sunDir  = new Vec3f((float) Math.sin(angle), (float) Math.cos(angle), 0.3f).normalize();

        // ---- 2. Compute sun screen-space UV for GodRaysPass ----------------
        // Transform sun world direction to view space: v = R * sunDir
        Mat4f R      = jobs.modelview;
        float vx     = R.m00() * sunDir.x + R.m01() * sunDir.y + R.m02() * sunDir.z;
        float vy     = R.m10() * sunDir.x + R.m11() * sunDir.y + R.m12() * sunDir.z;
        float vz     = R.m20() * sunDir.x + R.m21() * sunDir.y + R.m22() * sunDir.z;

        // Project to NDC: ndc = (P.m00 * vx / -vz,  P.m11 * vy / -vz)
        boolean sunInFront = vz < -0.001f;
        if (sunInFront) {
            float invNegVz   = 1.0f / (-vz);
            float ndcX       = jobs.perspective.m00() * vx * invNegVz;
            float ndcY       = jobs.perspective.m11() * vy * invNegVz;
            state.sunScreenX = ndcX * 0.5f + 0.5f;
            state.sunScreenY = ndcY * 0.5f + 0.5f;
            state.sunIntensity = Math.max(0f, sunDir.y);   // fade when below horizon
        } else {
            // Sun is behind the camera — push off-screen but keep X/Y valid
            state.sunScreenX   = -1f;
            state.sunScreenY   = -1f;
            state.sunIntensity = 0f;
        }

        // ---- 3. Draw sky quad ----------------------------------------------
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        glUseProgram(shader);

        // Inverse focal lengths: tan(fov/2)*aspect and tan(fov/2)
        float invFovX = (jobs.perspective.m00() != 0f) ? 1f / jobs.perspective.m00() : 1f;
        float invFovY = (jobs.perspective.m11() != 0f) ? 1f / jobs.perspective.m11() : 1f;
        glUniform1f(uInvFovX, invFovX);
        glUniform1f(uInvFovY, invFovY);
        glUniform1f(uTimeOfDay, state.timeOfDay);
        glUniform3f(uSunDir, sunDir.x, sunDir.y, sunDir.z);

        // Upload the 3x3 rotation part of modelview (column-major) as mat3.
        // In the shader, transpose(uViewRot) gives the inverse rotation
        // (world←view), needed to map view-space rays to world space.
        float[] rot3 = {
            R.m00(), R.m10(), R.m20(),   // col 0
            R.m01(), R.m11(), R.m21(),   // col 1
            R.m02(), R.m12(), R.m22()    // col 2
        };
        glUniformMatrix3fv(uViewRot, false, rot3);

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);

        glUseProgram(0);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);

        return Collections.emptyList();
    }

    // ---- Lazy GL init (called once on the GL thread) -----------------------

    private void initGL() {
        shader = buildShader();
        vao    = buildFullscreenQuad();

        glUseProgram(shader);
        uInvFovX   = glGetUniformLocation(shader, "uInvFovX");
        uInvFovY   = glGetUniformLocation(shader, "uInvFovY");
        uViewRot   = glGetUniformLocation(shader, "uViewRot");
        uSunDir    = glGetUniformLocation(shader, "uSunDir");
        uTimeOfDay = glGetUniformLocation(shader, "uTimeOfDay");
        glUseProgram(0);

        // Register the god rays pass into the post-process chain.
        layerManager.getPostProcessChain().addPass(new GodRaysPass(state));

        glReady = true;
    }

    // ---- GL resource creation ----------------------------------------------

    private int buildShader() {
        String vert = """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                out vec2 uv;
                void main() {
                    uv = aPos * 0.5 + 0.5;
                    // z = 1.0 → depth = 1.0 after perspective divide (far plane).
                    // glDepthMask(false) in the Java side means we never actually
                    // write this depth, but the sky colour always fills the background.
                    gl_Position = vec4(aPos, 1.0, 1.0);
                }
                """;

        String frag = """
                #version 330 core
                in  vec2 uv;
                out vec4 fragColor;

                uniform float uInvFovX;    // 1 / P.m00 = tan(fov/2) * aspect
                uniform float uInvFovY;    // 1 / P.m11 = tan(fov/2)
                uniform mat3  uViewRot;    // 3×3 rotation part of view matrix
                uniform vec3  uSunDir;     // world-space direction toward sun
                uniform float uTimeOfDay;  // 0–1, 0.5 = noon

                // ---- Sky colour model ----------------------------------------
                // tod: 0=midnight  0.25=dawn  0.5=noon  0.75=dusk
                vec3 skyColor(vec3 ray, vec3 sun, float tod) {
                    // Day factor peaks at noon (tod=0.5)
                    float dayF  = smoothstep(0.30, 0.48, tod)
                                - smoothstep(0.52, 0.70, tod);
                    // Dawn/dusk peaks near tod = 0.25 and 0.75
                    float ddF   = max(smoothstep(0.18, 0.25, tod) - smoothstep(0.25, 0.40, tod),
                                      smoothstep(0.60, 0.75, tod) - smoothstep(0.75, 0.82, tod));
                    float nightF = 1.0 - clamp(dayF + ddF, 0.0, 1.0);

                    // Colour palette
                    vec3 zenDay   = vec3(0.06, 0.28, 0.80);
                    vec3 horDay   = vec3(0.65, 0.85, 1.00);
                    vec3 zenDusk  = vec3(0.08, 0.06, 0.18);
                    vec3 horDusk  = vec3(0.95, 0.38, 0.08);
                    vec3 zenNight = vec3(0.01, 0.01, 0.05);
                    vec3 horNight = vec3(0.03, 0.03, 0.08);

                    vec3 zenith  = zenDay * dayF + zenDusk * ddF + zenNight * nightF;
                    vec3 horizon = horDay * dayF + horDusk * ddF + horNight * nightF;

                    float h = clamp(ray.y, 0.0, 1.0);
                    vec3  sky = mix(horizon, zenith, h);

                    // Ground fill below horizon
                    vec3 ground = vec3(0.14, 0.11, 0.09);
                    sky = mix(ground, sky, step(0.0, ray.y));

                    // Sun colour
                    vec3 sunCol = mix(vec3(1.0, 0.50, 0.15), vec3(1.0, 0.97, 0.80), dayF);

                    // Sun disc
                    float cosA  = dot(ray, sun);
                    float disc  = smoothstep(0.9994, 0.9998, cosA);
                    sky += disc * sunCol * 3.5 * step(0.0, sun.y);

                    // Halo (soft ring around sun)
                    float halo  = smoothstep(0.975, 0.999, cosA) * (1.0 - disc);
                    sky += halo * sunCol * 0.45 * step(0.0, sun.y);

                    return sky;
                }

                void main() {
                    // Reconstruct view-space ray from NDC fragment position
                    vec2  ndc     = uv * 2.0 - 1.0;
                    vec3  viewRay = normalize(vec3(ndc.x * uInvFovX,
                                                   ndc.y * uInvFovY,
                                                   -1.0));

                    // Rotate to world space: invView = transpose(uViewRot)
                    vec3  worldRay = normalize(transpose(uViewRot) * viewRay);

                    fragColor = vec4(skyColor(worldRay, uSunDir, uTimeOfDay), 1.0);
                }
                """;

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vert);
        glCompileShader(vs);
        checkShader(vs, "Sky vert");

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, frag);
        glCompileShader(fs);
        checkShader(fs, "Sky frag");

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private int buildFullscreenQuad() {
        float[] verts = { -1f, -1f,   1f, -1f,   1f,  1f,   -1f,  1f };
        int[]   idx   = { 0, 1, 2,   2, 3, 0 };

        int quadVao = glGenVertexArrays();
        int vbo     = glGenBuffers();
        int ebo     = glGenBuffers();

        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0L);
        glBindVertexArray(0);

        return quadVao;
    }

    private static void checkShader(int id, String name) {
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            System.err.println("[SkyLayer] Shader compile error in " + name + ":\n"
                               + glGetShaderInfoLog(id));
        }
    }
}
