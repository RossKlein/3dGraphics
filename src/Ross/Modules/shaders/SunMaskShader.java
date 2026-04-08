package Ross.Modules.shaders;

/**
 * Renders a white disc at the sun's screen-space UV position into an
 * offscreen framebuffer, producing the occlusion mask that the god-rays
 * radial blur operates on.
 *
 * Using a dedicated mask instead of the full scene colour buffer is what
 * prevents ghost copies of bright scene objects appearing as fake "rays".
 */
public class SunMaskShader extends Shader {

    private static final String VERT = "/shaders/sunmask/vertex.glsl";
    private static final String FRAG = "/shaders/sunmask/fragment.glsl";

    public SunMaskShader() {
        super(VERT, FRAG);
    }

    @Override
    protected void bindAttributes() {
        bindAttributeLocation(0, "aPos");
    }

    public void loadSunPos(float x, float y) {
        uniform2f("uSunPos", x, y);
    }

    /** 0 when the sun is below the horizon — fades the disc to black. */
    public void loadIntensity(float intensity) {
        uniform1f("uIntensity", intensity);
    }

    /** screenWidth / screenHeight — keeps the disc circular on non-square viewports. */
    public void loadAspect(float aspect) {
        uniform1f("uAspect", aspect);
    }
}
