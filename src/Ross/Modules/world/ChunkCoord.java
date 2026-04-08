package Ross.Modules.world;

import java.util.Objects;

/**
 * Immutable (cx, cz) integer key for a terrain chunk.
 *
 * Suitable as a {@link java.util.HashMap} key — {@code equals} and
 * {@code hashCode} are value-based.
 *
 * <h3>World-space convention</h3>
 * Chunk (cx, cz) covers the world-space square:
 * <pre>
 *   x ∈ [cx * CHUNK_SIZE, (cx+1) * CHUNK_SIZE)
 *   z ∈ [cz * CHUNK_SIZE, (cz+1) * CHUNK_SIZE)
 * </pre>
 */
public final class ChunkCoord {

    public final int cx;
    public final int cz;

    public ChunkCoord(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    // ---- World-space helpers -----------------------------------------------

    /** World-space X of this chunk's (0,0) corner. */
    public float worldX() { return cx * World.CHUNK_SIZE; }

    /** World-space Z of this chunk's (0,0) corner. */
    public float worldZ() { return cz * World.CHUNK_SIZE; }

    /** World-space X of this chunk's centre. */
    public float centerX() { return (cx + 0.5f) * World.CHUNK_SIZE; }

    /** World-space Z of this chunk's centre. */
    public float centerZ() { return (cz + 0.5f) * World.CHUNK_SIZE; }

    // ---- Distance helpers --------------------------------------------------

    /**
     * Chebyshev distance (king's-move) in chunk units.
     * Use this for load/unload radius checks — it gives a square region
     * which matches how we iterate the load grid.
     */
    public int chebyshevDistance(ChunkCoord other) {
        return Math.max(Math.abs(cx - other.cx), Math.abs(cz - other.cz));
    }

    /** Euclidean distance from this chunk's centre to a world-space point. */
    public float distanceTo(float worldX, float worldZ) {
        float dx = centerX() - worldX;
        float dz = centerZ() - worldZ;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    // ---- Factory -----------------------------------------------------------

    /**
     * Returns the chunk that contains the given world-space position.
     * Works for negative coordinates (floor division).
     */
    public static ChunkCoord fromWorld(float worldX, float worldZ) {
        return new ChunkCoord(
                (int) Math.floor(worldX / World.CHUNK_SIZE),
                (int) Math.floor(worldZ / World.CHUNK_SIZE));
    }

    // ---- Per-chunk RNG seed ------------------------------------------------

    /**
     * Derives a deterministic seed for this chunk's asset-placement RNG.
     *
     * Combines the world seed with a spatial hash of (cx, cz) so that every
     * chunk gets a unique, repeatable seed. Uses the splitmix64 finalizer to
     * avoid low-entropy clusters when cx/cz are small integers.
     *
     * @param worldSeed the global world seed stored in GameState / save file
     */
    public long chunkSeed(long worldSeed) {
        long h = worldSeed ^ (((long) cx) * 0x9E3779B97F4A7C15L)
                           ^ (((long) cz) * 0x6C62272E07BB0142L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    // ---- Object -----------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkCoord c)) return false;
        return cx == c.cx && cz == c.cz;
    }

    @Override
    public int hashCode() {
        // Cantor-pair style: low-collision for small integer pairs
        return Objects.hash(cx, cz);
    }

    @Override
    public String toString() {
        return "ChunkCoord(" + cx + ", " + cz + ")";
    }
}
