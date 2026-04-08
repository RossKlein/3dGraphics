package Ross.Modules.shaders;

/**
 * Shader for the god-rays composite pass.
 *
 * Expects two texture inputs:
 *   unit 0 — {@code uScene}:     the rendered scene colour buffer
 *   unit 1 — {@code uOcclusion}: the sun mask (white disc on black), produced
 *                                 by {@link SunMaskShader} each frame
 *
 * The radial blur operates on the occlusion mask, not the scene buffer,
 * so only the actual sun disc produces rays — no ghost copies, no scene haze.
 */
public class GodRaysShader extends Shader {

    private static final String VERT = "/shaders/godrays/vertex.glsl";
    private static final String FRAG = "/shaders/godrays/fragment.glsl";

    public GodRaysShader() {
        super(VERT, FRAG);
        startShader();
        uniform1i("uScene",     0);
        uniform1i("uOcclusion", 1);
        stopShader();
    }

    @Override
    protected void bindAttributes() {
        bindAttributeLocation(0, "aPos");
    }

    /** Sun screen-space UV in [0,1]. Values outside [0,1] push the source off-screen. */
    public void loadSunPos(float x, float y) {
        uniform2f("uSunPos", x, y);
    }

    /** 0 when the sun is below the horizon, 1 at zenith. */
    public void loadIntensity(float intensity) {
        uniform1f("uIntensity", intensity);
    }

    /** Sun tint — warm orange at dawn/dusk, pale yellow-white at noon. */
    public void loadSunColor(float r, float g, float b) {
        uniform3f("uSunColor", r, g, b);
    }
}
