package Ross.Modules.world;

/**
 * Maps (height, humidity) pairs to biomes, and provides the vertex color
 * palette used by {@link ChunkMesh} when building terrain geometry.
 *
 * <h3>Biome assignment</h3>
 * Height thresholds come from {@link World}. Humidity [0,1] breaks ties
 * between vegetation types at the same altitude band.
 *
 * <pre>
 * height < SEA_LEVEL                             → OCEAN
 * height < BEACH_LINE                            → BEACH
 * height >= ALPINE_LINE                          → ALPINE
 * height >= MOUNTAIN_LINE                        → MOUNTAINS
 * height >= HILL_LINE                            → HILLS
 * height in [BEACH..HILL], humidity < 0.3        → PLAINS
 * height in [BEACH..HILL], humidity in [0.3,0.6] → GRASSLAND
 * height in [BEACH..HILL], humidity >= 0.6       → FOREST
 * </pre>
 *
 * <h3>Vertex colors</h3>
 * Each biome has a low-color and a high-color. {@link ChunkMesh} blends
 * between them using a per-vertex noise value to break up flat-looking areas.
 * Slope darkening and snow blending are applied on top in the terrain shader.
 */
public final class BiomeMap {

    // ---- Biome enum -------------------------------------------------------

    public enum Biome {
        OCEAN,
        BEACH,
        PLAINS,
        GRASSLAND,
        FOREST,
        HILLS,
        MOUNTAINS,
        ALPINE
    }

    // ---- Static color tables (indexed by Biome.ordinal()) -----------------
    // Stored as flat float[] rather than per-call new float[]{...} to avoid
    // allocating a fresh array every time a vertex color is queried.
    // Previously colorLow()/colorHigh() each returned new float[4], causing
    // ~8 allocations + 3 lerp4() allocations per vertex — thousands of short-
    // lived objects per chunk build and the primary driver of GC pauses.

    private static final float[][] COLOR_LO = {
        { 0.10f, 0.25f, 0.55f, 1f }, // OCEAN
        { 0.82f, 0.76f, 0.55f, 1f }, // BEACH
        { 0.60f, 0.70f, 0.30f, 1f }, // PLAINS
        { 0.25f, 0.55f, 0.18f, 1f }, // GRASSLAND
        { 0.12f, 0.38f, 0.12f, 1f }, // FOREST
        { 0.45f, 0.50f, 0.25f, 1f }, // HILLS
        { 0.50f, 0.45f, 0.38f, 1f }, // MOUNTAINS
        { 0.85f, 0.87f, 0.90f, 1f }, // ALPINE
    };

    private static final float[][] COLOR_HI = {
        { 0.08f, 0.18f, 0.45f, 1f }, // OCEAN
        { 0.90f, 0.85f, 0.65f, 1f }, // BEACH
        { 0.70f, 0.75f, 0.35f, 1f }, // PLAINS
        { 0.30f, 0.62f, 0.22f, 1f }, // GRASSLAND
        { 0.08f, 0.28f, 0.10f, 1f }, // FOREST
        { 0.52f, 0.56f, 0.30f, 1f }, // HILLS
        { 0.58f, 0.52f, 0.44f, 1f }, // MOUNTAINS
        { 0.95f, 0.96f, 0.98f, 1f }, // ALPINE
    };

    private static final float[] ROCK_COLOR = { 0.42f, 0.40f, 0.36f, 1f };
    private static final float[] SNOW_COLOR = { 0.95f, 0.96f, 0.98f, 1f };

    // ---- Assignment -------------------------------------------------------

    private BiomeMap() { }

    /**
     * Returns the biome for a vertex with the given height and humidity.
     *
     * @param height   terrain height in world units (from HeightmapGenerator)
     * @param humidity [0, 1] from HeightmapGenerator.humidityAt()
     */
    public static Biome biomeAt(float height, float humidity) {
        if (height < World.SEA_LEVEL)     return Biome.OCEAN;
        if (height < World.BEACH_LINE)    return Biome.BEACH;
        if (height >= World.ALPINE_LINE)  return Biome.ALPINE;
        if (height >= World.MOUNTAIN_LINE) return Biome.MOUNTAINS;
        if (height >= World.HILL_LINE)    return Biome.HILLS;

        if (humidity < 0.30f) return Biome.PLAINS;
        if (humidity < 0.60f) return Biome.GRASSLAND;
        return Biome.FOREST;
    }

    /**
     * Writes the final blended RGBA vertex color for one terrain vertex
     * directly into {@code out[off..off+3]}.  No intermediate arrays are
     * allocated — all blending is done with local float variables.
     *
     * @param biome  biome assigned to this vertex
     * @param height vertex height (for snow blending)
     * @param slope  surface slope [0,1] — 0 = flat, 1 = vertical cliff
     * @param noise  per-vertex noise [0,1] — breaks up uniform areas
     * @param out    destination array (must have length >= off+4)
     * @param off    write offset into {@code out}
     */
    public static void vertexColor(Biome biome, float height, float slope,
                                   float noise, float[] out, int off) {
        float[] lo = COLOR_LO[biome.ordinal()];
        float[] hi = COLOR_HI[biome.ordinal()];

        // Blend lo→hi using per-vertex noise for natural variation
        float r = lo[0] + noise * (hi[0] - lo[0]);
        float g = lo[1] + noise * (hi[1] - lo[1]);
        float b = lo[2] + noise * (hi[2] - lo[2]);

        // Slope darkening: steep faces reveal rocky material
        float sf = clamp(slope * 1.8f, 0f, 1f);
        r = r + sf * (ROCK_COLOR[0] - r);
        g = g + sf * (ROCK_COLOR[1] - g);
        b = b + sf * (ROCK_COLOR[2] - b);

        // Snow blending above snow line
        if (height > World.SNOW_LINE) {
            float snowT = clamp((height - World.SNOW_LINE) / 30f, 0f, 1f);
            r = r + snowT * (SNOW_COLOR[0] - r);
            g = g + snowT * (SNOW_COLOR[1] - g);
            b = b + snowT * (SNOW_COLOR[2] - b);
        }

        out[off    ] = r;
        out[off + 1] = g;
        out[off + 2] = b;
        out[off + 3] = 1f;
    }

    // ---- Utilities --------------------------------------------------------

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
