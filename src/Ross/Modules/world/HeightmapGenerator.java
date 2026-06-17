package Ross.Modules.world;

import java.util.Random;

/**
 * Generates deterministic terrain from a stack of control fields.
 *
 * <p>The generator intentionally does <b>not</b> treat terrain as one blended
 * noise soup. Instead it builds low-frequency control maps first, then applies
 * interpreted terrain rules (coastal profile, inland relief, cliffs, detail)
 * through masks. This keeps landform identity coherent while still allowing
 * variation.</p>
 *
 * <h3>Why world-space sampling is the key to seamless chunks</h3>
 * Every noise sample is made at <em>world-space</em> coordinates, not
 * chunk-local coordinates. This means:
 * <ul>
 *   <li>Chunk (3,7) and chunk (4,7) share identical values along their
 *       shared edge with no extra stitching code.</li>
 *   <li>Regenerating any chunk from the same seed always produces exactly
 *       the same heightmap — no disk storage needed for unmodified terrain.</li>
 * </ul>
 *
 * <h3>Two separate noise functions</h3>
 * Height and humidity use independent {@link PerlinNoise} instances seeded
 * differently. Combining them gives the 2D biome map described in
 * {@code world-generation.md}.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * HeightmapGenerator gen = new HeightmapGenerator(worldSeed);
 *
 * // Generate full-resolution heightmap for chunk (cx, cz):
 * float[][] heights  = gen.generateHeightmap(cx, cz);
 * float[][] humidity = gen.generateHumidityMap(cx, cz);
 * }</pre>
 *
 * Both arrays are {@link World#HEIGHTMAP_SIZE} × {@link World#HEIGHTMAP_SIZE}
 * (65×65). Index [x][z] maps to world position
 * {@code (cx*CHUNK_SIZE + x, cz*CHUNK_SIZE + z)}.
 */
public final class HeightmapGenerator {

    private final PerlinNoise heightNoise;
    private final PerlinNoise humidityNoise;

    public HeightmapGenerator(long worldSeed) {
        heightNoise   = new PerlinNoise(worldSeed);
        humidityNoise = new PerlinNoise(worldSeed ^ 0xDEADBEEFCAFEBABEL);
    }

    // ---- Public API -------------------------------------------------------

    /**
     * Generates the full-resolution heightmap for chunk {@code (cx, cz)}.
     *
     * @return float[x][z], indices 0…HEIGHTMAP_SIZE-1, height in world units
     */
    public float[][] generateHeightmap(int cx, int cz) {
        int size = World.HEIGHTMAP_SIZE;
        float[][] map = new float[size][size];
        float originX = cx * World.CHUNK_SIZE;
        float originZ = cz * World.CHUNK_SIZE;
        float step    = World.CHUNK_SIZE / (size - 1);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float wx = originX + x * step;
                float wz = originZ + z * step;
                map[x][z] = heightAt(wx, wz);
            }
        }
        return map;
    }

    /**
     * Generates the humidity map for chunk {@code (cx, cz)}.
     *
     * @return float[x][z] in range [0, 1], same indexing as heightmap
     */
    public float[][] generateHumidityMap(int cx, int cz) {
        int size = World.HEIGHTMAP_SIZE;
        float[][] map = new float[size][size];
        float originX = cx * World.CHUNK_SIZE;
        float originZ = cz * World.CHUNK_SIZE;
        float step    = World.CHUNK_SIZE / (size - 1);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float wx = originX + x * step;
                float wz = originZ + z * step;
                map[x][z] = humidityAt(wx, wz);
            }
        }
        return map;
    }

    /**
     * Height at an arbitrary world-space point. Safe to call from any thread.
     * Used by the physics system and asset placement to query height outside
     * the grid.
     */
    public float heightAt(float wx, float wz) {
        // ---- Stage 1: world-scale control fields --------------------------
        float warpedX = wx + heightNoise.fbm(wx * 0.00080f, wz * 0.00080f, 2, 2.0f, 0.5f) * 120f;
        float warpedZ = wz + heightNoise.fbm((wx + 913.17f) * 0.00080f, (wz - 271.5f) * 0.00080f, 2, 2.0f, 0.5f) * 120f;

        float continent = 0.70f * heightNoise.fbm(warpedX * 0.00027f, warpedZ * 0.00027f, 4, 2.0f, 0.52f)
                        + 0.30f * heightNoise.fbm(warpedX * 0.00012f, warpedZ * 0.00012f, 3, 2.0f, 0.55f);

        // Signed coast proxy: negative offshore, positive inland.
        float coastSignal = continent - 0.05f;
        float coastDist = coastSignal * 950f;

        float cliffness = smooth01(remap01(heightNoise.fbm((wx + 1043f) * 0.00062f, (wz - 502f) * 0.00062f, 3, 2.0f, 0.5f)));
        float hilliness = smooth01(remap01(heightNoise.fbm((wx - 287f) * 0.00048f, (wz + 777f) * 0.00048f, 3, 2.0f, 0.5f)));
        float uplift = smooth01(remap01(heightNoise.fbm((wx + 161f) * 0.00030f, (wz + 991f) * 0.00030f, 3, 2.0f, 0.56f)));

        float ruggedRaw = ridged(heightNoise, wx * 0.00095f, wz * 0.00095f, 3, 2.1f, 0.53f);
        float ruggedness = smooth01(clamp01(ruggedRaw * 0.95f + 0.05f));

        // ---- Stage 2: coastal base profile --------------------------------
        float nearCoastMask = clamp01(1f - Math.abs(coastDist) / 320f);
        float beachWidth = lerp(130f, 18f, cliffness);

        float coastalBase;
        if (coastDist < 0f) {
            // Underwater shelf: gentle near shore, deepens offshore.
            float oceanDepth = -coastDist;
            float shelfT = clamp01(oceanDepth / 220f);
            float depthSlope = lerp(0.08f, 0.18f, shelfT) * lerp(0.9f, 1.25f, cliffness);
            coastalBase = World.SEA_LEVEL - oceanDepth * depthSlope;
        } else if (coastDist < beachWidth) {
            // Beach band: broad on soft coasts, narrow on cliff coasts.
            float t = coastDist / Math.max(1f, beachWidth);
            coastalBase = lerp(World.SEA_LEVEL + 0.5f, World.BEACH_LINE, smooth01(t));
        } else {
            // Inland rise from beach line with profile controlled by cliffness/uplift.
            float inland = coastDist - beachWidth;
            float rise = inland * lerp(0.05f, 0.28f, cliffness) * lerp(0.80f, 1.35f, uplift);
            float envelope = (float) Math.pow(Math.max(0f, inland / 260f), 0.65f) * 65f * cliffness;
            coastalBase = World.BEACH_LINE + rise + envelope;
        }

        // ---- Stage 3: inland macro relief ---------------------------------
        float inlandMask = smoothstep(20f, 260f, coastDist);

        float broadRelief = smooth01(remap01(heightNoise.fbm((wx - 1400f) * 0.00022f, (wz + 440f) * 0.00022f, 3, 2.0f, 0.55f)));
        broadRelief = (float) Math.pow(broadRelief, 1.35f) * lerp(90f, 420f, uplift);

        float hills = heightNoise.fbm(wx * 0.0021f, wz * 0.0021f, 4, 2.0f, 0.50f)
                    * lerp(18f, 85f, hilliness)
                    * (1f - ruggedness * 0.65f);

        float ridges = (float) Math.pow(ridged(heightNoise, wx * 0.00130f, wz * 0.00130f, 4, 2.0f, 0.5f), 1.8)
                     * lerp(20f, 260f, ruggedness)
                     * lerp(0.35f, 1.10f, uplift);

        // ---- Stage 4: context-aware feature masks -------------------------
        float beachMask = nearCoastMask
                        * (1f - cliffness)
                        * smoothstep(-20f, 35f, coastDist);

        float cliffMask = nearCoastMask
                        * cliffness
                        * smoothstep(12f, 220f, coastDist)
                        * lerp(0.55f, 1.0f, uplift);

        float cliffBreak = (float) Math.pow(ridged(heightNoise, wx * 0.0042f, wz * 0.0042f, 2, 2.0f, 0.5f), 1.4)
                         * 68f * cliffMask;

        // ---- Stage 5: local detail by context -----------------------------
        float baseDetail = heightNoise.fbm(wx * 0.020f, wz * 0.020f, 2, 2.0f, 0.5f);
        float detailAmp = lerp(2f, 15f, ruggedness)
                        * lerp(0.15f, 1f, inlandMask)
                        * (1f - beachMask * 0.85f);
        float detail = baseDetail * detailAmp;

        float dunes = heightNoise.fbm((wx + 300f) * 0.035f, (wz - 300f) * 0.035f, 2, 2.0f, 0.5f)
                    * 2.5f * beachMask;

        float macro = inlandMask * (broadRelief + hills + ridges);
        float height = coastalBase + macro + cliffBreak + detail + dunes;

        // Keep deep ocean from becoming unrealistically deep in one sample.
        return Math.max(-420f, height);
    }

    /**
     * Humidity at an arbitrary world-space point. Returns [0, 1].
     */
    public float humidityAt(float wx, float wz) {
        float warpedX = wx + humidityNoise.fbm(wx * 0.00070f, wz * 0.00070f, 2, 2.0f, 0.5f) * 90f;
        float warpedZ = wz + humidityNoise.fbm((wx - 712f) * 0.00070f, (wz + 219f) * 0.00070f, 2, 2.0f, 0.5f) * 90f;

        float climate = remap01(humidityNoise.fbm(warpedX * 0.00028f, warpedZ * 0.00028f, 4, 2.0f, 0.52f));
        float local = remap01(humidityNoise.fbm(wx * 0.0018f, wz * 0.0018f, 2, 2.0f, 0.5f));

        float continent = 0.70f * heightNoise.fbm(warpedX * 0.00027f, warpedZ * 0.00027f, 4, 2.0f, 0.52f)
                        + 0.30f * heightNoise.fbm(warpedX * 0.00012f, warpedZ * 0.00012f, 3, 2.0f, 0.55f);
        float coastDist = (continent - 0.05f) * 950f;
        float coastWetness = clamp01(1f - Math.abs(coastDist) / 650f);

        float uplift = smooth01(remap01(heightNoise.fbm((wx + 161f) * 0.00030f, (wz + 991f) * 0.00030f, 3, 2.0f, 0.56f)));
        float ruggedness = smooth01(clamp01(ridged(heightNoise, wx * 0.00095f, wz * 0.00095f, 3, 2.1f, 0.53f)));
        float rainShadow = uplift * ruggedness;

        float h = 0.62f * climate + 0.18f * local + 0.24f * coastWetness - 0.20f * rainShadow;
        return clamp01(h);
    }

    // ---- Terrain helpers --------------------------------------------------

    private static float remap01(float v) {
        return clamp01((v + 1f) * 0.5f);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smooth01(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return x < edge0 ? 0f : 1f;
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    /**
     * Ridged multifractal helper: returns [0, 1].
     */
    private static float ridged(PerlinNoise noise, float x, float y,
                                int octaves, float lacunarity, float persistence) {
        float sum = 0f;
        float amp = 1f;
        float freq = 1f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            float n = noise.noise(x * freq, y * freq);
            float r = 1f - Math.abs(n);
            r *= r;
            sum += r * amp;
            norm += amp;
            amp *= persistence;
            freq *= lacunarity;
        }
        return norm > 0f ? sum / norm : 0f;
    }

    // ---- Perlin Noise implementation --------------------------------------

    /**
     * Classic Perlin gradient noise, seeded via a shuffled permutation table.
     *
     * References:
     * - Ken Perlin, "Improving Noise" (SIGGRAPH 2002)
     * - Reference implementation: mrl.cs.nyu.edu/~perlin/noise/
     */
    static final class PerlinNoise {

        private final int[] perm = new int[512];

        PerlinNoise(long seed) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;

            // Fisher-Yates shuffle driven by the seed
            Random rng = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                int t = p[i]; p[i] = p[j]; p[j] = t;
            }
            // Double the table to avoid index-wrapping in the hot path
            for (int i = 0; i < 256; i++) {
                perm[i] = perm[i + 256] = p[i];
            }
        }

        /**
         * Single-octave 2-D Perlin noise. Returns approximately [-1, 1].
         */
        float noise(float x, float y) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;

            x -= (float) Math.floor(x);
            y -= (float) Math.floor(y);

            float u = fade(x);
            float v = fade(y);

            int aa = perm[perm[X    ] + Y    ];
            int ab = perm[perm[X    ] + Y + 1];
            int ba = perm[perm[X + 1] + Y    ];
            int bb = perm[perm[X + 1] + Y + 1];

            return lerp(v,
                    lerp(u, grad(aa, x,     y    ),
                            grad(ba, x - 1, y    )),
                    lerp(u, grad(ab, x,     y - 1),
                            grad(bb, x - 1, y - 1)));
        }

        /**
         * Fractal Brownian Motion — sum of {@code octaves} noise layers.
         *
         * @param octaves    number of layers (more = more detail, more cost)
         * @param lacunarity frequency multiplier per octave (typically 2.0)
         * @param persistence amplitude multiplier per octave (typically 0.5)
         */
        float fbm(float x, float y, int octaves, float lacunarity, float persistence) {
            float value = 0f;
            float amp   = 1f;
            float freq  = 1f;
            float max   = 0f;  // for normalisation

            for (int i = 0; i < octaves; i++) {
                value += noise(x * freq, y * freq) * amp;
                max   += amp;
                amp   *= persistence;
                freq  *= lacunarity;
            }
            return value / max; // normalise to roughly [-1, 1]
        }

        // -- Perlin internals --

        private static float fade(float t) {
            return t * t * t * (t * (t * 6f - 15f) + 10f); // 6t^5 - 15t^4 + 10t^3
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }

        /**
         * Converts a hash to a 2-D gradient direction and dots it with (x, y).
         * 4 gradient vectors: (±1, 0), (0, ±1) — simple but effective.
         */
        private static float grad(int hash, float x, float y) {
            // Use lower 2 bits to pick one of 4 gradient directions
            switch (hash & 3) {
                case 0: return  x + y;
                case 1: return -x + y;
                case 2: return  x - y;
                default: return -x - y;
            }
        }
    }
}
