package Ross.Modules.shaders;

import Ross.Modules.math.Mat4f;
import Ross.Modules.math.Vec3f;

/**
 * Shader for the geometry clipmap terrain.
 *
 * <h3>Per-ring uniforms (set once per ring per frame)</h3>
 * <ul>
 *   <li>{@code heightHumTex}  — sampler2D bound to texture unit 0</li>
 *   <li>{@code texOriginX/Y} — toroidal read offset (integers)</li>
 *   <li>{@code ringCenter}   — camera-relative XZ of ring center</li>
 *   <li>{@code ringStep}     — world units per cell</li>
 *   <li>{@code ringOriginStepX/Y} — world step-coord of cell ix=0, iz=0 (for noise)</li>
 *   <li>{@code camY}         — camera Y offset (jobs.position.y())</li>
 * </ul>
 *
 * <h3>Per-frame uniforms</h3>
 * <ul>
 *   <li>{@code v, p}         — view / projection matrices</li>
 *   <li>{@code sunDir}       — world-space normalised sun direction</li>
 *   <li>{@code uTimeOfDay}   — 0=midnight, 0.25=dawn, 0.5=noon, 0.75=dusk</li>
 *   <li>{@code hazeStart / hazeDensity}</li>
 * </ul>
 */
public class TerrainShader extends Shader {

    private static final String VERT = "/shaders/terrain/vertex.glsl";
    private static final String FRAG = "/shaders/terrain/fragment.glsl";

    public TerrainShader() {
        super(VERT, FRAG);
        // Bind sampler uniforms to their fixed texture units (never change).
        startShader();
        uniform1i("heightHumTex", 0);  // per-ring toroidal height/humidity
        uniform1i("sandColTex",   1);  // PBR albedo
        uniform1i("sandNrmTex",   2);  // PBR normal map
        uniform1i("sandAoTex",    3);  // PBR ambient occlusion
        stopShader();
    }

    @Override
    protected void bindAttributes() {
        // No vertex attributes — vertex shader uses gl_VertexID only.
    }

    @Override
    public void loadMatrix(Mat4f matrix, String name, boolean transpose) {
        bindUniformMatrix4fvLocation(matrix, name, transpose);
    }

    /** World-space normalised sun direction. */
    public void loadSunDir(Vec3f dir) {
        uniform3f("sunDir", dir.x(), dir.y(), dir.z());
    }

    /** Atmospheric haze parameters (see fragment shader). */
    public void loadHaze(float timeOfDay, float start, float density) {
        uniform1f("uTimeOfDay",  timeOfDay);
        uniform1f("hazeStart",   start);
        uniform1f("hazeDensity", density);
    }

    /**
     * Upload all per-ring uniforms.
     *
     * @param texOriginX      toroidal column offset (ring column 0 → this texture column)
     * @param texOriginZ      toroidal row offset
     * @param camRelCenterX   camera-relative X of ring center
     * @param camRelCenterZ   camera-relative Z of ring center
     * @param camY            camera Y offset (jobs.position.y())
     * @param step            world units per cell
     * @param originStepX     world step-coordinate of ring column ix=0
     * @param originStepZ     world step-coordinate of ring row iz=0
     */
    public void loadRing(int texOriginX, int texOriginZ,
                         float camRelCenterX, float camRelCenterZ,
                         float camY, float step,
                         int originStepX, int originStepZ) {
        uniform1i("texOriginX",      texOriginX);
        uniform1i("texOriginY",      texOriginZ);
        uniform2f("ringCenter",      camRelCenterX, camRelCenterZ);
        uniform1f("camY",            camY);
        uniform1f("ringStep",        step);
        uniform1i("ringOriginStepX", originStepX);
        uniform1i("ringOriginStepY", originStepZ);
    }
}
