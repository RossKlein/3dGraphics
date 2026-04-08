package Ross.Instance;

import Ross.Modules.Job;
import Ross.Modules.JobModule;
import Ross.Modules.Settings;
import Ross.Modules.math.Vec3f;
import Ross.Modules.scene.BaseLayer;
import Ross.Modules.scene.GameState;
import Ross.Modules.scene.RenderTarget;
import Ross.Modules.shaders.TerrainShader;
import Ross.Modules.world.HeightmapGenerator;
import Ross.Modules.world.TerrainRing;
import Ross.Modules.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * Geometry clipmap terrain renderer.
 *
 * <h3>Architecture</h3>
 * Five {@link TerrainRing} instances cover LOD levels 0–4.  Each ring is a
 * fixed {@code 255 × 255} quad grid backed by a {@code 256 × 256 RG32F}
 * toroidal texture storing (height, humidity) per cell.
 *
 * <p>As the camera moves, only the newly exposed strip of rows or columns is
 * recomputed and uploaded via {@code glTexSubImage2D} — {@code O(N)} work per
 * moved step, not {@code O(N²)}.  No dynamic allocation, no pool overflow,
 * no per-chunk state machine.</p>
 *
 * <h3>Rendering</h3>
 * Rings are drawn coarsest-first (LOD 4 → 0).  The depth test ensures finer
 * rings correctly overwrite coarser ones wherever they overlap.  Five draw
 * calls per frame total.
 *
 * <h3>Texturing</h3>
 * Texture unit 0 is the per-ring toroidal heightmap. Units 1-3 are the shared
 * PBR sand material (COL, NRM, AO), tiled at a fixed world-space scale so they
 * remain stable across all LOD levels.
 */
public class TerrainLayer extends BaseLayer {

    private static final String TEX_ROOT = "/test/GroundSand005/GroundSand005_";

    private final GameState          state;
    private final HeightmapGenerator gen;

    private TerrainShader shader;
    private TerrainRing[] rings;
    private int sandColTex, sandNrmTex, sandAoTex;
    private boolean glReady = false;

    public TerrainLayer(GameState state, long worldSeed) {
        this.state = state;
        this.gen   = new HeightmapGenerator(worldSeed);
    }

    // ---- Layer identity ---------------------------------------------------

    @Override public Group  getGroup() { return Group.WORLD; }
    @Override public int    getOrder() { return 1; }
    @Override public String getName()  { return "terrain"; }

    // ---- Lifecycle --------------------------------------------------------

    @Override
    public List<Job> onPop(JobModule jobs) {
        if (glReady) {
            for (TerrainRing ring : rings) ring.dispose();
            shader.exit();
            GL11.glDeleteTextures(new int[]{ sandColTex, sandNrmTex, sandAoTex });
        }
        return Collections.emptyList();
    }

    // ---- Update (update thread) ------------------------------------------

    @Override
    public List<Job> update(JobModule jobs) {
        return Collections.emptyList();
    }

    // ---- Render (GL thread) ----------------------------------------------

    @Override
    public List<Job> render(JobModule jobs, RenderTarget target, double dt) {
        if (!glReady) initGL();

        float camX = -jobs.position.x();
        float camZ = -jobs.position.z();
        float camY =  jobs.position.y();

        if (!jobs.paused && jobs.getUtils() != null) {
            jobs.xrotate = (float) (jobs.getUtils().xvel / 4.0);
            jobs.yrotate = (float) (jobs.getUtils().yvel / 4.0);
            jobs.fov     = (float)  jobs.getUtils().fov;
        }

        for (TerrainRing ring : rings) {
            if (!ring.ready) ring.fillAll(camX, camZ);
        }
        for (TerrainRing ring : rings) {
            ring.update(camX, camZ);
        }

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);

        if (Settings.wireframe) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        }

        shader.startShader();
        shader.loadMatrix(jobs.modelview,   "v", false);
        shader.loadMatrix(jobs.perspective, "p", false);

        float angle  = (float) ((state.timeOfDay - 0.5) * 2.0 * Math.PI);
        Vec3f sunDir = new Vec3f(
                (float) Math.sin(angle),
                (float) Math.cos(angle),
                0.3f
        ).normalize();
        shader.loadSunDir(sunDir);
        shader.loadHaze((float) state.timeOfDay, 1500f, 0.005f);

        // Bind shared PBR textures (units 1-3) once for all rings
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sandColTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sandNrmTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sandAoTex);

        // Render coarsest → finest
        for (int lod = World.LOD_COUNT - 1; lod >= 0; lod--) {
            TerrainRing ring = rings[lod];
            if (!ring.ready) continue;

            ring.bindTexture(0);   // binds to unit 0
            shader.loadRing(
                    ring.texOriginX, ring.texOriginZ,
                    ring.camRelCenterX(camX), ring.camRelCenterZ(camZ),
                    camY, ring.step,
                    ring.originStepX(), ring.originStepZ()
            );
            ring.draw();
        }

        shader.stopShader();

        if (Settings.wireframe) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }

        return Collections.emptyList();
    }

    // ---- Initialisation (GL thread) --------------------------------------

    private void initGL() {
        if (glReady) return;
        shader     = new TerrainShader();
        rings      = new TerrainRing[World.LOD_COUNT];
        for (int lod = 0; lod < World.LOD_COUNT; lod++) {
            rings[lod] = new TerrainRing(lod, gen);
            rings[lod].initGL();
        }
        sandColTex = loadTexture(TEX_ROOT + "COL_2K.jpg");
        sandNrmTex = loadTexture(TEX_ROOT + "NRM_2K.jpg");
        sandAoTex  = loadTexture(TEX_ROOT + "AO_2K.jpg");
        glReady = true;
    }

    // ---- Texture loading --------------------------------------------------

    /**
     * Loads a JPEG/PNG texture from the classpath, generates mipmaps, and
     * returns its OpenGL texture ID.  Must be called on the GL thread.
     */
    private static int loadTexture(String classpathPath) {
        URL url = TerrainLayer.class.getResource(classpathPath);
        if (url == null) throw new RuntimeException("Terrain texture not found on classpath: " + classpathPath);
        String fsPath;
        try {
            fsPath = new java.io.File(url.toURI()).getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve texture path: " + classpathPath, e);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer c = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(false);
            ByteBuffer pixels = STBImage.stbi_load(fsPath, w, h, c, 3);
            if (pixels == null) {
                throw new RuntimeException("STB failed to load [" + classpathPath + "]: "
                        + STBImage.stbi_failure_reason());
            }

            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB,
                    w.get(0), h.get(0), 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            STBImage.stbi_image_free(pixels);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            System.out.println("[TerrainLayer] Loaded texture: " + classpathPath
                    + " (" + w.get(0) + "×" + h.get(0) + ")");
            return id;
        }
    }
}
