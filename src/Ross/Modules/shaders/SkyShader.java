package Ross.Modules.shaders;

import Ross.Modules.math.Vec3f;

/**
 * Shader for the procedural sky background pass.
 *
 * Loads GLSL from {@code res/shaders/sky/} and exposes typed setters
 * for every uniform so callers never touch raw GL uniform calls.
 */
public class SkyShader extends Shader {

    private static final String VERT = "/shaders/sky/vertex.glsl";
    private static final String FRAG = "/shaders/sky/fragment.glsl";

    public SkyShader() {
        super(VERT, FRAG);
    }

    @Override
    protected void bindAttributes() {
        bindAttributeLocation(0, "aPos");
    }

    public void loadInvFov(float invFovX, float invFovY) {
        uniform1f("uInvFovX", invFovX);
        uniform1f("uInvFovY", invFovY);
    }

    /**
     * Upload the 3x3 rotation part of the view matrix (column-major).
     * The shader transposes this to get the inverse (world-from-view) rotation.
     */
    public void loadViewRot(float[] columnMajor3x3) {
        uniformMatrix3fv("uViewRot", false, columnMajor3x3);
    }

    public void loadSunDir(Vec3f dir) {
        bindUniform3vLocation(dir, "uSunDir");
    }

    public void loadTimeOfDay(float tod) {
        uniform1f("uTimeOfDay", tod);
    }
}
