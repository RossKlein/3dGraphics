package Ross.Modules.world;

/**
 * Maps Chebyshev distance (in chunks) to a LOD index.
 *
 * <p>LOD 0 = full resolution (65×65 grid)<br>
 * LOD 4 = lowest resolution (5×5 grid)<br>
 * Returns -1 when the chunk is beyond the visible range (should not be drawn).</p>
 *
 * <h3>Ring geometry</h3>
 * Each LOD is assigned a Chebyshev-distance shell.  The number of chunks in a
 * shell from exclusive inner radius {@code dPrev} to inclusive outer radius
 * {@code d} is {@code (2d+1)² − (2·dPrev+1)²}.
 * These per-LOD counts drive the {@link TerrainVboPool} capacity so GPU buffer
 * memory matches exactly what the current render distance needs.
 */
public final class LODSelector {

    private LODSelector() {}

    /**
     * Maximum Chebyshev distance (inclusive) for each LOD level.
     * LOD_MAX_DIST[i] is the outermost chunk ring that uses LOD i.
     *
     * <p><strong>Keep in sync:</strong> the last entry must equal
     * {@link World#LOAD_RADIUS}.  If you change LOAD_RADIUS, update this
     * array and the comments in {@link #chunkCountForLod}.</p>
     */
    public static final int[] LOD_MAX_DIST = { 3, 6, 10, 15, World.LOAD_RADIUS };

    /**
     * Number of chunks in the Chebyshev shell assigned to {@code lod}.
     *
     * <pre>
     *   lod 0 :  (2·3+1)²                       =   49
     *   lod 1 :  (2·6+1)²  − (2·3+1)²           =  120
     *   lod 2 :  (2·10+1)² − (2·6+1)²           =  272
     *   lod 3 :  (2·15+1)² − (2·10+1)²          =  520
     *   lod 4 :  (2·24+1)² − (2·15+1)²          = 1440
     * </pre>
     */
    public static int chunkCountForLod(int lod) {
        int outer = 2 * LOD_MAX_DIST[lod] + 1;
        int inner = lod == 0 ? 0 : (2 * LOD_MAX_DIST[lod - 1] + 1);
        return outer * outer - inner * inner;
    }

    /**
     * @param chunk   the chunk whose LOD is being decided
     * @param player  the chunk the player is currently standing in
     * @return LOD index [0..4], or -1 if out of visible range
     */
    public static int select(ChunkCoord chunk, ChunkCoord player) {
        int d = chunk.chebyshevDistance(player);
        for (int lod = 0; lod < LOD_MAX_DIST.length; lod++) {
            if (d <= LOD_MAX_DIST[lod]) return lod;
        }
        return -1;
    }
}
