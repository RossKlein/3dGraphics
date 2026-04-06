package Ross.Modules.scene;

import static org.lwjgl.opengl.GL33.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the post-processing pipeline between the world FBO and the screen.
 *
 * Pipeline (called from {@link LayerManager#renderFrame}):
 *
 *   worldFBO color texture
 *         ↓
 *   pass[0] → ping FBO
 *         ↓
 *   pass[1] → pong FBO
 *         ↓
 *      ...
 *         ↓
 *   pass[n-1] → screen
 *
 * With zero passes: world FBO is blitted directly to screen via a passthrough
 * fullscreen quad (no glBlitFramebuffer — stays consistent with the shader path
 * and avoids format/size constraints).
 *
 * Must be initialized on the GL thread via {@link #init}.
 */
public class PostProcessChain {

    private final List<PostProcessPass> passes = new ArrayList<>();

    // Ping-pong FBOs for multi-pass chains — lazily created when the first
    // multi-pass chain runs, recreated on resize.
    private Framebuffer[] pingPong = null;
    private int screenWidth;
    private int screenHeight;

    // Passthrough blit shader + fullscreen quad VAO
    private int blitShader;
    private int blitVao;
    private boolean initialized = false;

    // ---- Init / resize -----------------------------------------------------

    /** Must be called once on the GL thread before the first {@link #run} call. */
    public void init(int screenWidth, int screenHeight) {
        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;
        buildBlitShader();
        buildFullscreenQuad();
        initialized = true;
    }

    public void resize(int width, int height) {
        screenWidth  = width;
        screenHeight = height;
        if (pingPong != null) {
            for (Framebuffer fb : pingPong) fb.dispose();
            pingPong = null;
        }
        for (PostProcessPass p : passes) p.resize(width, height);
    }

    // ---- Pass management ---------------------------------------------------

    public void addPass(PostProcessPass pass)    { passes.add(pass); }
    public void removePass(PostProcessPass pass) { passes.remove(pass); }

    // ---- Per-frame ---------------------------------------------------------

    /**
     * Run the chain. Called from the GL thread after all WORLD layers have
     * rendered into {@code worldFBO} and before SCREEN layers are drawn.
     *
     * Leaves the default framebuffer (0) bound on return.
     */
    public void run(Framebuffer worldFBO) {
        if (!initialized) return;

        if (passes.isEmpty()) {
            // Fast path: blit world FBO straight to screen
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, screenWidth, screenHeight);
            blit(worldFBO.getColorTexture());
            return;
        }

        ensurePingPong();

        int inputTex = worldFBO.getColorTexture();
        for (int i = 0; i < passes.size(); i++) {
            boolean lastPass = (i == passes.size() - 1);
            if (lastPass) {
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                glViewport(0, 0, screenWidth, screenHeight);
                passes.get(i).run(inputTex, null);
            } else {
                Framebuffer out = pingPong[i % 2];
                out.bind();
                glViewport(0, 0, out.getWidth(), out.getHeight());
                glClear(GL_COLOR_BUFFER_BIT);
                passes.get(i).run(inputTex, out);
                inputTex = out.getColorTexture();
            }
        }
    }

    // ---- Cleanup -----------------------------------------------------------

    public void dispose() {
        if (pingPong != null) {
            for (Framebuffer fb : pingPong) fb.dispose();
        }
        for (PostProcessPass p : passes) p.dispose();
        glDeleteProgram(blitShader);
        glDeleteVertexArrays(blitVao);
    }

    // ---- Helpers -----------------------------------------------------------

    /** Draw a fullscreen quad textured with the given GL texture ID. */
    private void blit(int texture) {
        glDisable(GL_DEPTH_TEST);
        glUseProgram(blitShader);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindVertexArray(blitVao);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0L);
        glBindVertexArray(0);
        glUseProgram(0);
        glEnable(GL_DEPTH_TEST);
    }

    private void ensurePingPong() {
        if (pingPong == null) {
            pingPong = new Framebuffer[]{
                new Framebuffer(screenWidth, screenHeight),
                new Framebuffer(screenWidth, screenHeight)
            };
        }
    }

    // ---- GL resource creation ----------------------------------------------

    private void buildBlitShader() {
        String vert = """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                out vec2 uv;
                void main() {
                    uv = aPos * 0.5 + 0.5;
                    gl_Position = vec4(aPos, 0.0, 1.0);
                }
                """;
        String frag = """
                #version 330 core
                in vec2 uv;
                out vec4 fragColor;
                uniform sampler2D uInput;
                void main() {
                    fragColor = texture(uInput, uv);
                }
                """;

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vert);
        glCompileShader(vs);

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, frag);
        glCompileShader(fs);

        blitShader = glCreateProgram();
        glAttachShader(blitShader, vs);
        glAttachShader(blitShader, fs);
        glLinkProgram(blitShader);
        glDeleteShader(vs);
        glDeleteShader(fs);

        glUseProgram(blitShader);
        glUniform1i(glGetUniformLocation(blitShader, "uInput"), 0);
        glUseProgram(0);
    }

    private void buildFullscreenQuad() {
        // NDC quad: covers [-1, 1] in both X and Y
        float[] verts = { -1f, -1f,   1f, -1f,   1f,  1f,   -1f,  1f };
        int[]   idx   = { 0, 1, 2,   2, 3, 0 };

        blitVao = glGenVertexArrays();
        int vbo = glGenBuffers();
        int ebo = glGenBuffers();

        glBindVertexArray(blitVao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, idx, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0L);
        glBindVertexArray(0);
        // vbo/ebo stay attached to the VAO; no need to delete separately
    }
}
