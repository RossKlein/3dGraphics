package Ross.Modules.scene;

import Ross.Modules.shaders.GodRaysShader;
import Ross.Modules.shaders.SunMaskShader;

import static org.lwjgl.opengl.GL33.*;

/**
 * Post-processing pass that adds volumetric light shafts ("god rays") using
 * the Crytek radial-blur technique (GPU Gems 3, Chapter 13) — done correctly
 * with a dedicated sun occlusion mask rather than the full scene colour buffer.
 *
 * Two internal sub-passes each frame:
 *
 *   1. Sun mask — renders a white disc at the sun's screen-space UV position
 *      into a half-resolution internal FBO.  Everything else is black.
 *
 *   2. Radial blur + composite — blurs the sun mask radially toward the sun
 *      position, then adds the tinted result additively onto the scene.
 *
 * Because only the sun disc is blurred (not the full scene), there are no
 * ghost copies of horizon objects and no global haze.
 *
 * The pass reads sun state from {@link GameState}, written by
 * {@link Ross.Instance.SkyLayer} earlier in the same frame.
 */
public class GodRaysPass implements PostProcessPass {

    private final GameState state;

    // ---- Pass 1: sun mask ---------------------------------------------------
    private SunMaskShader  sunMaskShader;
    private FullscreenQuad sunMaskQuad;
    private Framebuffer    sunMaskFbo;
    private int            maskW = 0;
    private int            maskH = 0;

    // ---- Pass 2: radial blur + composite -----------------------------------
    private GodRaysShader  shader;
    private FullscreenQuad quad;

    // Screen dimensions — updated by resize(), used when output == null (last pass).
    private int screenW = 0;
    private int screenH = 0;

    private boolean initialized = false;

    public GodRaysPass(GameState state) {
        this.state = state;
    }

    // ---- PostProcessPass ---------------------------------------------------

    @Override
    public void run(int inputTexture, RenderTarget output) {
        ensureInitialized();

        // Determine output dimensions (for viewport restore and FBO sizing).
        int outW = (output != null) ? output.getWidth()  : screenW;
        int outH = (output != null) ? output.getHeight() : screenH;

        // Guard: if we still don't know the screen size, skip this frame.
        if (outW <= 0 || outH <= 0) return;

        float aspect = (float) outW / outH;
        ensureSunMaskFbo(Math.max(1, outW / 2), Math.max(1, outH / 2));

        // ---- Pass 1: render sun disc into the sun mask FBO -----------------
        sunMaskFbo.bind();
        glViewport(0, 0, maskW, maskH);
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        sunMaskShader.startShader();
        sunMaskShader.loadSunPos(state.sunScreenX, state.sunScreenY);
        sunMaskShader.loadIntensity(state.sunIntensity);
        sunMaskShader.loadAspect(aspect);
        sunMaskQuad.draw();
        sunMaskShader.stopShader();

        // ---- Restore the output target the chain already bound -------------
        if (output != null) {
            output.bind();
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        glViewport(0, 0, outW, outH);

        // ---- Pass 2: radial blur on sun mask, additive composite -----------
        glDisable(GL_DEPTH_TEST);

        shader.startShader();
        shader.loadSunPos(state.sunScreenX, state.sunScreenY);
        shader.loadIntensity(state.sunIntensity);
        shader.loadSunColor(state.sunColorR, state.sunColorG, state.sunColorB);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, inputTexture);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, sunMaskFbo.getColorTexture());

        quad.draw();

        shader.stopShader();

        // Always leave TEXTURE0 active to match GL conventions.
        glActiveTexture(GL_TEXTURE0);
        glEnable(GL_DEPTH_TEST);
    }

    @Override
    public void resize(int width, int height) {
        screenW = width;
        screenH = height;
        // Destroy the sun mask FBO so it gets recreated at the new size next frame.
        if (sunMaskFbo != null) {
            sunMaskFbo.dispose();
            sunMaskFbo = null;
            maskW = 0;
            maskH = 0;
        }
    }

    @Override
    public void dispose() {
        if (initialized) {
            sunMaskShader.exit();
            sunMaskQuad.dispose();
            shader.exit();
            quad.dispose();
        }
        if (sunMaskFbo != null) {
            sunMaskFbo.dispose();
            sunMaskFbo = null;
        }
    }

    // ---- Helpers -----------------------------------------------------------

    private void ensureInitialized() {
        if (initialized) return;
        sunMaskShader = new SunMaskShader();
        sunMaskQuad   = new FullscreenQuad();
        shader        = new GodRaysShader();
        quad          = new FullscreenQuad();
        initialized   = true;
    }

    private void ensureSunMaskFbo(int w, int h) {
        if (sunMaskFbo != null && maskW == w && maskH == h) return;
        if (sunMaskFbo != null) sunMaskFbo.dispose();
        sunMaskFbo = new Framebuffer(w, h);
        maskW = w;
        maskH = h;
    }
}
