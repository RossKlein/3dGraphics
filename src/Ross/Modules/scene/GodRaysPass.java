package Ross.Modules.scene;

import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing pass that adds volumetric light shafts ("god rays") using
 * the Crytek radial-blur technique (GPU Gems 3, Chapter 13).
 *
 * Each frame, 64 samples are taken along the screen-space line from the
 * current pixel toward the sun's projected screen position, accumulating
 * scattered light from the scene colour buffer (additive blend).
 *
 * Usage:
 * <pre>{@code
 * GodRaysPass godRays = new GodRaysPass(gameState);
 * layerManager.getPostProcessChain().addPass(godRays);
 * }</pre>
 *
 * The pass reads {@code state.sunScreenX / sunScreenY / sunIntensity} every
 * frame; these are written by {@link Ross.Instance.SkyLayer} before the
 * post-process chain runs.
 */
public class GodRaysPass implements PostProcessPass {

    private final GameState state;

    private int shader;
    private int vao;
    private int uSunPos;
    private int uIntensity;
    private boolean initialized = false;

    public GodRaysPass(GameState state) {
        this.state = state;
    }

    // ---- PostProcessPass ---------------------------------------------------

    @Override
    public void run(int inputTexture, RenderTarget output) {
        ensureInitialized();

        glDisable(GL_DEPTH_TEST);
        glUseProgram(shader);

        glUniform2f(uSunPos,    state.sunScreenX, state.sunScreenY);
        glUniform1f(uIntensity, state.sunIntensity);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTexture);

        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);

        glUseProgram(0);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    public void resize(int width, int height) {
        // No size-dependent state — uniform is updated every frame in run().
    }

    @Override
    public void dispose() {
        if (initialized) {
            glDeleteProgram(shader);
            glDeleteVertexArrays(vao);
        }
    }

    // ---- Lazy GL init ------------------------------------------------------

    private void ensureInitialized() {
        if (initialized) return;
        shader = buildShader();
        vao    = buildFullscreenQuad();

        glUseProgram(shader);
        glUniform1i(glGetUniformLocation(shader, "uScene"), 0);
        uSunPos    = glGetUniformLocation(shader, "uSunPos");
        uIntensity = glGetUniformLocation(shader, "uIntensity");
        glUseProgram(0);

        initialized = true;
    }

    // ---- GL resource creation ----------------------------------------------

    private int buildShader() {
        String vert = """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                out vec2 uv;
                void main() {
                    uv = aPos * 0.5 + 0.5;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
                """;

        // Crytek light shaft technique — GPU Gems 3 §13.
        // Radial blur toward uSunPos, decay over distance.
        String frag = """
                #version 330 core
                in vec2 uv;
                out vec4 fragColor;

                uniform sampler2D uScene;
                uniform vec2      uSunPos;   // sun UV in [0,1]
                uniform float     uIntensity;

                const int   NUM_SAMPLES = 64;
                const float DECAY       = 0.97;
                const float WEIGHT      = 0.015;
                const float EXPOSURE    = 0.9;

                void main() {
                    vec2 delta      = (uv - uSunPos) / float(NUM_SAMPLES);
                    vec2 sampleUv   = uv;
                    float decay     = 1.0;
                    vec3  rays      = vec3(0.0);

                    for (int i = 0; i < NUM_SAMPLES; i++) {
                        sampleUv    -= delta;
                        vec3 s       = texture(uScene, clamp(sampleUv, 0.0, 1.0)).rgb;
                        rays        += s * decay * WEIGHT;
                        decay       *= DECAY;
                    }

                    vec4 scene   = texture(uScene, uv);
                    fragColor    = scene + vec4(rays * EXPOSURE * uIntensity, 0.0);
                }
                """;

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vert);
        glCompileShader(vs);
        checkShader(vs, "GodRays vert");

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, frag);
        glCompileShader(fs);
        checkShader(fs, "GodRays frag");

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
            System.err.println("[GodRaysPass] Shader compile error in " + name + ":\n"
                               + glGetShaderInfoLog(id));
        }
    }
}
