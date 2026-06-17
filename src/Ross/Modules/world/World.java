package Ross.Modules.world;

/**
 * World constants and (eventually) the chunk map + streaming manager.
 *
 * Constants are defined here as the single source of truth so that
 * ChunkCoord, ChunkMesh, LODSelector, and the shaders all agree.
 *
 * Full streaming/management logic (update loop, load/unload radii, instance
 * batches) will be added here once the individual chunk subsystems are tested.
 */
public final class World {

    private World() { }

    // ---- Spatial constants ------------------------------------------------

    /** Side length of one chunk in world units. */
    public static final float CHUNK_SIZE = 64f;

    /**
     * Number of vertices along one side of a full-resolution (LOD 0) chunk.
     * Quads = HEIGHTMAP_SIZE - 1 = 64.
     * At CHUNK_SIZE=64 each LOD-0 quad covers 64/64 = 1 world unit.
     */
    public static final int HEIGHTMAP_SIZE = 65;

    /** Number of LOD levels pre-generated per chunk (0 = full, 4 = coarsest). */
    public static final int LOD_COUNT = 5;

    /**
     * Vertex count along one side for each LOD level.
     * LOD 0 = 65, LOD 1 = 33, LOD 2 = 17, LOD 3 = 9, LOD 4 = 5.
     */
    public static final int[] LOD_GRID_SIZES = { 65, 33, 17, 9, 5 };

    // ---- Geometry clipmap constants ---------------------------------------

    /**
     * Quads per side of each clipmap ring. RING_VERTS = RING_QUADS + 1 = 256
     * (power of 2 — allows fast toroidal wrap via bitwise AND in the shader).
     * Each ring's GPU texture is RING_VERTS × RING_VERTS.
     */
    public static final int RING_QUADS = 255;
    public static final int RING_VERTS = 256;   // texture size per ring

    /**
     * World units per cell for each clipmap LOD level.
     * Ring L covers RING_QUADS * LOD_STEP[L] world units per side:
     *   LOD 0: 255 ×  1 =  255 units (~4 chunks)
     *   LOD 1: 255 ×  2 =  510 units (~8 chunks)
     *   LOD 2: 255 ×  4 = 1020 units (~16 chunks)
     *   LOD 3: 255 ×  8 = 2040 units (~32 chunks)
     *   LOD 4: 255 × 16 = 4080 units (~64 chunks)
     */
    public static final int[] LOD_STEP = { 1, 2, 4, 8, 16 };

    // ---- Load radii (in chunk units) -------------------------------------

    /** Start generating any chunk within this many chunks of the camera.
     *  (2r+1)² chunks are loaded simultaneously — 16 = 1089, 24 = 2401, 32 = 4225.
     *  Each chunk uses ~350 KB GPU memory; at r=24 that is ~840 MB.
     *  With CHUNK_SIZE=128 this covers 24×128=3072 world units in each direction. */
    public static final int LOAD_RADIUS   = 64;

    /** Unload any chunk further than this from the camera. */
    public static final int UNLOAD_RADIUS = 80;

    // ---- Sea level and biome thresholds (world units) --------------------

    public static final float SEA_LEVEL      =    0f;
    public static final float BEACH_LINE     =   20f;
    public static final float HILL_LINE      =  250f;
    public static final float MOUNTAIN_LINE  =  600f;
    public static final float ALPINE_LINE    =  900f;
    public static final float SNOW_LINE      =  800f;
}
