package Ross.Modules.world;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * One level of a geometry clipmap.
 *
 * <h3>Geometry</h3>
 * A static {@code RING_QUADS × RING_QUADS} grid (255 × 255 quads,
 * 256 × 256 vertices). Vertex positions are computed entirely in the
 * vertex shader from {@code gl_VertexID} — no position VBO is needed.
 *
 * <h3>Height texture</h3>
 * A {@code RING_VERTS × RING_VERTS} (256 × 256) {@code RG32F} texture
 * stores {@code (height, humidity)} for each cell. The texture wraps
 * toroidally: as the camera moves, only the newly exposed strip of
 * columns or rows is rewritten with {@code glTexSubImage2D}.
 *
 * <h3>Toroidal addressing</h3>
 * {@link #texOriginX} / {@link #texOriginZ} track which texture column/row
 * corresponds to ring cell {@code ix=0} / {@code iz=0}. The vertex shader
 * reads texel {@code ((texOriginX + ix) & (NV-1), (texOriginZ + iz) & (NV-1))}.
 *
 * <h3>Threading</h3>
 * All methods that touch GL state must run on the GL thread.
 */
public final class TerrainRing {

    // ---- Ring identity ----------------------------------------------------

    public final int   lod;
    public final int   NV;     // vertices per side = World.RING_VERTS = 256
    public final int   NQ;     // quads per side    = World.RING_QUADS = 255
    public final float step;   // world units per cell = World.LOD_STEP[lod]

    // ---- Skirt constants (must match vertex.glsl) -------------------------

    /**
     * Regular vertex IDs: 0 .. NV*NV-1  (ix = vid/NV, iz = vid%NV).
     *
     * Outer skirt vertices start here.  Each of the 4 outer edges stores NV
     * sunk copies of its edge vertices, laid out as:
     *   edge 0 (iz=0,  top):    OUTER_BASE + 0*NV + ix   → ix=0..NV-1, iz=0
     *   edge 1 (iz=NQ, bottom): OUTER_BASE + 1*NV + ix   → ix=0..NV-1, iz=NQ
     *   edge 2 (ix=0,  left):   OUTER_BASE + 2*NV + iz   → ix=0, iz=0..NV-1
     *   edge 3 (ix=NQ, right):  OUTER_BASE + 3*NV + iz   → ix=NQ, iz=0..NV-1
     */
    public static final int OUTER_BASE  = World.RING_VERTS * World.RING_VERTS; // 65536

    /**
     * Hole (inner ring perimeter) skirt vertex base.  Only used in IBO for LOD > 0.
     * Layout (IHW = HOLE_END - HOLE_START = 126 vertices per inner edge):
     *   edge 0 (iz=HOLE_START, inner top):    INNER_BASE + 0*IHW + j
     *   edge 1 (iz=HOLE_END-1, inner bottom): INNER_BASE + 1*IHW + j
     *   edge 2 (ix=HOLE_START, inner left):   INNER_BASE + 2*IHW + j
     *   edge 3 (ix=HOLE_END-1, inner right):  INNER_BASE + 3*IHW + j
     * where j = 0..IHW-1.
     */
    public static final int HOLE_START  = 65;
    public static final int HOLE_END    = World.RING_VERTS - HOLE_START; // 191
    public static final int IHW         = HOLE_END - HOLE_START;         // 126
    public static final int INNER_BASE  = OUTER_BASE + 4 * World.RING_VERTS; // 66560

    // ---- GL handles -------------------------------------------------------

    private int texId;         // RG32F toroidal texture
    private int vaoId;         // VAO (no VBOs — vertex shader uses gl_VertexID)
    private int eboId;         // index buffer
    public  int indexCount;    // total indices including skirts

    // ---- Toroidal state ---------------------------------------------------

    /** Texture column that corresponds to ring cell ix=0. */
    public int texOriginX;
    /** Texture row that corresponds to ring cell iz=0. */
    public int texOriginZ;

    /**
     * Ring center in step-units.
     * World X of ring cell ix: {@code (centerStepX - NV/2 + ix) * step}.
     */
    int centerStepX, centerStepZ;

    // ---- Status -----------------------------------------------------------

    /** False until {@link #fillAll} has completed at least once. */
    public boolean ready = false;

    // ---- Per-frame reusable upload buffer ---------------------------------

    /** Pre-allocated buffer for one strip (NV texels × 2 channels). */
    private FloatBuffer stripBuf;

    // ---- Dependencies -----------------------------------------------------

    private final HeightmapGenerator gen;

    // -----------------------------------------------------------------------

    public TerrainRing(int lod, HeightmapGenerator gen) {
        this.lod  = lod;
        this.NV   = World.RING_VERTS;
        this.NQ   = World.RING_QUADS;
        this.step = World.LOD_STEP[lod];
        this.gen  = gen;
    }

    // ---- GL lifecycle -----------------------------------------------------

    /** Allocate texture, VAO, and IBO. Must be on the GL thread. */
    public void initGL() {
        // Texture: RG32F, NV×NV, toroidal (GL_REPEAT)
        texId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG32F,
                NV, NV, 0, GL30.GL_RG, GL11.GL_FLOAT, (FloatBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // VAO (vertex-less — only an IBO; vertex shader derives positions from gl_VertexID)
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // ---- IBO layout -------------------------------------------------------
        //
        // Section 1 — main terrain grid (same as before, HOLE_START/END are the
        //             static constants defined above):
        //   LOD 0: full NQ×NQ grid
        //   LOD 1-4: hollow frame omitting inner HOLE_START..HOLE_END-1 square
        //
        // Why holeStart=65 (not 64):
        //   Rings snap centers to their own step size; worst-case misalignment
        //   between adjacent rings = step_coarse/2.  Fine outer radius = 127.5 *
        //   step_fine = 63.75 * step_coarse.  holeStart=64 → hole radius exactly
        //   64 * step_coarse, so the fine ring can be short by 0.25 * step_coarse
        //   under worst misalignment.  holeStart=65 → 63 * step_coarse hole radius,
        //   guaranteeing ≥ 0.75 * step_coarse overlap in all cases.
        //
        // Section 2 — outer-perimeter skirts (all 4 edges, all LODs):
        //   Sunk copies of edge vertices hang down by ringStep * 4 world units
        //   (computed in the vertex shader from the skirt vertex ID range).
        //   Winding verified CCW for each edge's outward normal direction.
        //
        // Section 3 — inner-hole skirts (LOD > 0 only):
        //   Sunk copies of the hole-perimeter vertices bridge the gap between
        //   the coarse ring's inner boundary and the finer ring's outer boundary.

        int frameQuads = (lod == 0) ? (NQ * NQ)
                                    : (NQ * NQ - IHW * IHW);
        int outerSkirtIndices = 4 * NQ * 6;
        indexCount = frameQuads * 6 + outerSkirtIndices;

        IntBuffer ib = MemoryUtil.memAllocInt(indexCount);

        // ---- Section 1: main grid -------------------------------------------
        for (int ix = 0; ix < NQ; ix++) {
            for (int iz = 0; iz < NQ; iz++) {
                if (lod > 0
                        && ix >= HOLE_START && ix < HOLE_END
                        && iz >= HOLE_START && iz < HOLE_END) {
                    continue;
                }
                // CCW winding → upward normal (matches original terrain)
                int v0 =  ix      * NV + iz;
                int v1 =  ix      * NV + iz + 1;
                int v2 = (ix + 1) * NV + iz;
                int v3 = (ix + 1) * NV + iz + 1;
                ib.put(v0).put(v1).put(v2);
                ib.put(v1).put(v3).put(v2);
            }
        }

        // ---- Section 2: outer-perimeter skirts ------------------------------
        // Skirts face INWARD (toward the ring center / the player), so they are
        // visible when the player looks outward from the ring's interior.
        // This covers the height-seam gap at each ring boundary as seen from
        // the player's perspective.
        //
        // Winding gives CCW normal pointing toward the ring center:
        //   top    (iz=0):  inward = +Z → T(v0,s0,v1) T(v1,s0,s1)
        //   bottom (iz=NQ): inward = -Z → T(v0,v1,s0) T(v1,s1,s0)
        //   left   (ix=0):  inward = +X → T(v0,v1,s0) T(v1,s1,s0)
        //   right  (ix=NQ): inward = -X → T(v0,s0,v1) T(v1,s0,s1)

        // Top (iz = 0, inward normal = +Z)
        for (int ix = 0; ix < NQ; ix++) {
            int v0 =  ix      * NV;
            int v1 = (ix + 1) * NV;
            int s0 = OUTER_BASE           + ix;
            int s1 = OUTER_BASE           + ix + 1;
            ib.put(v0).put(s0).put(v1);
            ib.put(v1).put(s0).put(s1);
        }

        // Bottom (iz = NQ, inward normal = -Z)
        for (int ix = 0; ix < NQ; ix++) {
            int v0 =  ix      * NV + NQ;
            int v1 = (ix + 1) * NV + NQ;
            int s0 = OUTER_BASE + NV      + ix;
            int s1 = OUTER_BASE + NV      + ix + 1;
            ib.put(v0).put(v1).put(s0);
            ib.put(v1).put(s1).put(s0);
        }

        // Left (ix = 0, inward normal = +X)
        for (int iz = 0; iz < NQ; iz++) {
            int v0 = iz;
            int v1 = iz + 1;
            int s0 = OUTER_BASE + 2 * NV + iz;
            int s1 = OUTER_BASE + 2 * NV + iz + 1;
            ib.put(v0).put(v1).put(s0);
            ib.put(v1).put(s1).put(s0);
        }

        // Right (ix = NQ, inward normal = -X)
        for (int iz = 0; iz < NQ; iz++) {
            int v0 = NQ * NV + iz;
            int v1 = NQ * NV + iz + 1;
            int s0 = OUTER_BASE + 3 * NV + iz;
            int s1 = OUTER_BASE + 3 * NV + iz + 1;
            ib.put(v0).put(s0).put(v1);
            ib.put(v1).put(s0).put(s1);
        }

        ib.flip();
        eboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        GL30.glBindVertexArray(0);

        // Reusable strip upload buffer (one column or row of NV texels, 2 channels)
        stripBuf = MemoryUtil.memAllocFloat(NV * 2);
    }

    /** Free all GL resources. Must be on the GL thread. */
    public void dispose() {
        GL30.glDeleteVertexArrays(vaoId);
        GL15.glDeleteBuffers(eboId);
        GL11.glDeleteTextures(texId);
        MemoryUtil.memFree(stripBuf);
    }

    // ---- Texture fill -----------------------------------------------------

    /**
     * Fill the entire texture from scratch for a new camera position.
     * Called on first frame and after large camera jumps. GL thread only.
     */
    public void fillAll(float camX, float camZ) {
        centerStepX = Math.round(camX / step);
        centerStepZ = Math.round(camZ / step);
        texOriginX  = 0;
        texOriginZ  = 0;

        // Build NV×NV texture data: texel (ix, iz) = world pos for ring cell (ix, iz)
        float[] data = new float[NV * NV * 2];
        for (int ix = 0; ix < NV; ix++) {
            float wx = (centerStepX - NV / 2 + ix) * step;
            for (int iz = 0; iz < NV; iz++) {
                float wz = (centerStepZ - NV / 2 + iz) * step;
                // OpenGL texture layout: row = iz (y), column = ix (x)
                int off = (iz * NV + ix) * 2;
                data[off    ] = gen.heightAt(wx, wz);
                data[off + 1] = gen.humidityAt(wx, wz);
            }
        }

        FloatBuffer fb = MemoryUtil.memAllocFloat(data.length);
        try {
            fb.put(data).flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, NV, NV,
                    GL30.GL_RG, GL11.GL_FLOAT, fb);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            MemoryUtil.memFree(fb);
        }

        ready = true;
    }

    /**
     * Move the ring to follow the camera, filling any newly exposed strips.
     * Each world step the camera takes in X exposes one column; Z exposes one row.
     * GL thread only.
     */
    public void update(float camX, float camZ) {
        if (!ready) return;

        int newCX = Math.round(camX / step);
        int newCZ = Math.round(camZ / step);
        int dx = newCX - centerStepX;
        int dz = newCZ - centerStepZ;
        if (dx == 0 && dz == 0) return;

        // Large jump (e.g., teleport) — full re-fill
        if (Math.abs(dx) >= NV || Math.abs(dz) >= NV) {
            fillAll(camX, camZ);
            return;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);

        // ---- X strips ----
        for (int i = 0, absDx = Math.abs(dx); i < absDx; i++) {
            if (dx > 0) {
                // Moving right: old leftmost texel column becomes new rightmost
                int texCol = texOriginX;           // column to overwrite
                centerStepX++;
                texOriginX = (texOriginX + 1) & (NV - 1);
                // World X of new rightmost ring cell (ix = NV-1, now mapped to texCol):
                float wx = (centerStepX - NV / 2 + (NV - 1)) * step;
                uploadColumn(texCol, wx);
            } else {
                // Moving left: old rightmost texel column becomes new leftmost
                centerStepX--;
                texOriginX = (texOriginX - 1 + NV) & (NV - 1);
                int texCol = texOriginX;
                float wx = (centerStepX - NV / 2) * step;
                uploadColumn(texCol, wx);
            }
        }

        // ---- Z strips ----
        for (int i = 0, absDz = Math.abs(dz); i < absDz; i++) {
            if (dz > 0) {
                int texRow = texOriginZ;
                centerStepZ++;
                texOriginZ = (texOriginZ + 1) & (NV - 1);
                float wz = (centerStepZ - NV / 2 + (NV - 1)) * step;
                uploadRow(texRow, wz);
            } else {
                centerStepZ--;
                texOriginZ = (texOriginZ - 1 + NV) & (NV - 1);
                int texRow = texOriginZ;
                float wz = (centerStepZ - NV / 2) * step;
                uploadRow(texRow, wz);
            }
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // ---- Strip upload helpers ---------------------------------------------

    /**
     * Fill one texture column ({@code 1 × NV} texels) at {@code texCol}
     * with heights sampled at world X = {@code wx}, all current Z values.
     */
    private void uploadColumn(int texCol, float wx) {
        stripBuf.clear();
        for (int texIz = 0; texIz < NV; texIz++) {
            // Inverse-toroidal: which ring row iz does texIz correspond to?
            int iz = (texIz - texOriginZ + NV) & (NV - 1);
            float wz = (centerStepZ - NV / 2 + iz) * step;
            stripBuf.put(gen.heightAt(wx, wz));
            stripBuf.put(gen.humidityAt(wx, wz));
        }
        stripBuf.flip();
        // Upload 1-pixel-wide column: x=texCol, y=0, w=1, h=NV
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                texCol, 0, 1, NV, GL30.GL_RG, GL11.GL_FLOAT, stripBuf);
    }

    /**
     * Fill one texture row ({@code NV × 1} texels) at {@code texRow}
     * with heights sampled at world Z = {@code wz}, all current X values.
     */
    private void uploadRow(int texRow, float wz) {
        stripBuf.clear();
        for (int texIx = 0; texIx < NV; texIx++) {
            int ix = (texIx - texOriginX + NV) & (NV - 1);
            float wx = (centerStepX - NV / 2 + ix) * step;
            stripBuf.put(gen.heightAt(wx, wz));
            stripBuf.put(gen.humidityAt(wx, wz));
        }
        stripBuf.flip();
        // Upload 1-pixel-tall row: x=0, y=texRow, w=NV, h=1
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0,
                0, texRow, NV, 1, GL30.GL_RG, GL11.GL_FLOAT, stripBuf);
    }

    // ---- Render -----------------------------------------------------------

    /** Bind height/humidity texture to the given texture unit. */
    public void bindTexture(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
    }

    /** Issue the draw call. The caller is responsible for shader setup. */
    public void draw() {
        GL30.glBindVertexArray(vaoId);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }

    // ---- World-space helpers (for shader uniforms) -----------------------

    /** Camera-relative X of this ring's center, given the camera's world X. */
    public float camRelCenterX(float camWorldX) {
        return centerStepX * step - camWorldX;
    }

    /** Camera-relative Z of this ring's center, given the camera's world Z. */
    public float camRelCenterZ(float camWorldZ) {
        return centerStepZ * step - camWorldZ;
    }

    /**
     * World step-coordinate of ring column ix=0.
     * Used by the vertex shader to compute a world-stable noise hash.
     */
    public int originStepX() { return centerStepX - NV / 2; }
    public int originStepZ() { return centerStepZ - NV / 2; }
}
