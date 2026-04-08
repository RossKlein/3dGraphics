package Ross.Modules.world;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.*;

/**
 * Binary serialization for chunk heightmap data.
 *
 * <h3>Design rationale</h3>
 * Pure-noise terrain is always reproducible from the world seed, so
 * unmodified chunks never need saving. Only chunks marked {@link Chunk#dirty}
 * (player-modified terrain) are written to disk. On load, the serializer is
 * checked first; if no file exists the generator produces the terrain fresh.
 *
 * This gives us the game-file foundation for terrain editing while paying
 * zero I/O cost for the common case.
 *
 * <h3>File format (version 1)</h3>
 * All integers are little-endian.
 * <pre>
 *  Offset  Size  Field
 *  ------  ----  -----
 *       0     4  magic   — 0x434E484B ("CHNK")
 *       4     4  version — currently 1
 *       8     4  cx      — chunk X coordinate (signed int)
 *      12     4  cz      — chunk Z coordinate (signed int)
 *      16     4  size    — heightmap side length (e.g. 65)
 *      20     1  flags   — bit 0: dirty/modified
 *      21     3  padding — reserved, write as 0
 *      24  size²×4  heightmap — float[x][z], row-major, IEEE 754 LE
 * </pre>
 *
 * <h3>Directory layout</h3>
 * <pre>
 *   saves/&lt;worldName&gt;/world.dat       — seed + spawn position
 *   saves/&lt;worldName&gt;/chunks/cx_cz.dat — one file per dirty chunk
 * </pre>
 */
public final class ChunkSerializer {

    // ---- Constants --------------------------------------------------------

    private static final int  MAGIC      = 0x434E484B;  // "CHNK"
    private static final int  VERSION    = 1;
    private static final int  HEADER_BYTES = 24;

    // ---- Configuration ----------------------------------------------------

    private final Path chunksDir;

    /**
     * @param worldName folder name under {@code saves/} — safe to use the
     *                  world seed as a hex string, e.g. {@code "world_deadbeef"}
     */
    public ChunkSerializer(String worldName) {
        this.chunksDir = Paths.get("saves", worldName, "chunks");
    }

    // ---- Public API -------------------------------------------------------

    /**
     * Returns true if a saved file exists for this chunk.
     * A false result means the chunk should be generated from noise.
     */
    public boolean exists(ChunkCoord coord) {
        return Files.exists(chunkPath(coord));
    }

    /**
     * Load the heightmap for {@code coord} from disk.
     *
     * @return float[HEIGHTMAP_SIZE][HEIGHTMAP_SIZE], or {@code null} if the
     *         file does not exist or is corrupt (caller should regenerate).
     */
    public float[][] load(ChunkCoord coord) {
        Path path = chunkPath(coord);
        if (!Files.exists(path)) return null;

        try (FileChannel fc = FileChannel.open(path, READ)) {
            int size = World.HEIGHTMAP_SIZE;
            int expectedBytes = HEADER_BYTES + size * size * 4;

            ByteBuffer buf = ByteBuffer.allocate(expectedBytes)
                                       .order(ByteOrder.LITTLE_ENDIAN);
            int read = 0;
            while (buf.hasRemaining()) {
                int n = fc.read(buf);
                if (n < 0) break;
                read += n;
            }
            buf.flip();

            if (read < HEADER_BYTES) return null;  // truncated

            // Validate header
            int magic   = buf.getInt();
            int version = buf.getInt();
            int fileCx  = buf.getInt();
            int fileCz  = buf.getInt();
            int fileSize = buf.getInt();
            buf.getInt();  // flags + padding (4 bytes combined)

            if (magic != MAGIC || version != VERSION) {
                System.err.println("[ChunkSerializer] Bad magic/version in " + path);
                return null;
            }
            if (fileCx != coord.cx || fileCz != coord.cz) {
                System.err.println("[ChunkSerializer] Coord mismatch in " + path);
                return null;
            }
            if (fileSize != size) {
                System.err.println("[ChunkSerializer] Size mismatch in " + path
                        + " (expected " + size + ", got " + fileSize + ")");
                return null;
            }

            // Read heightmap
            float[][] heightmap = new float[size][size];
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    if (buf.remaining() < 4) return null;  // truncated
                    heightmap[x][z] = buf.getFloat();
                }
            }
            return heightmap;

        } catch (IOException e) {
            System.err.println("[ChunkSerializer] Failed to load " + path + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Save the chunk's heightmap to disk.
     *
     * Only call this when {@link Chunk#dirty} is true — pure-noise chunks
     * don't need saving. After a successful save, {@code chunk.dirty} is
     * reset to {@code false}.
     *
     * Thread-safe: uses an atomic replace (write temp file → rename).
     */
    public void save(Chunk chunk) {
        if (!chunk.dirty || chunk.heightmap == null) return;

        ChunkCoord coord = chunk.coord;
        int size = World.HEIGHTMAP_SIZE;

        try {
            Files.createDirectories(chunksDir);

            Path   path     = chunkPath(coord);
            Path   tempPath = path.resolveSibling(coord.cx + "_" + coord.cz + ".tmp");

            int totalBytes = HEADER_BYTES + size * size * 4;
            ByteBuffer buf = ByteBuffer.allocate(totalBytes)
                                       .order(ByteOrder.LITTLE_ENDIAN);

            // Header
            buf.putInt(MAGIC);
            buf.putInt(VERSION);
            buf.putInt(coord.cx);
            buf.putInt(coord.cz);
            buf.putInt(size);
            buf.putInt(chunk.dirty ? 1 : 0);  // flags byte + 3 padding bytes packed

            // Heightmap data
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    buf.putFloat(chunk.heightmap[x][z]);
                }
            }
            buf.flip();

            // Write to temp, then atomic rename — avoids corrupt partial writes
            try (FileChannel fc = FileChannel.open(tempPath, CREATE, WRITE, TRUNCATE_EXISTING)) {
                while (buf.hasRemaining()) fc.write(buf);
            }
            Files.move(tempPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                       java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            chunk.dirty = false;

        } catch (IOException e) {
            System.err.println("[ChunkSerializer] Failed to save " + coord + ": " + e.getMessage());
        }
    }

    /**
     * Delete the saved file for a chunk (e.g. when reverting terrain edits).
     */
    public void delete(ChunkCoord coord) {
        try {
            Files.deleteIfExists(chunkPath(coord));
        } catch (IOException e) {
            System.err.println("[ChunkSerializer] Failed to delete " + coord + ": " + e.getMessage());
        }
    }

    // ---- World metadata ---------------------------------------------------

    /**
     * Write the world seed and spawn position to {@code saves/<name>/world.dat}.
     * Call once when a new world is created.
     */
    public void saveWorldMeta(long seed, float spawnX, float spawnZ) {
        Path worldDir = chunksDir.getParent();
        Path metaPath = worldDir.resolve("world.dat");
        try {
            Files.createDirectories(worldDir);
            ByteBuffer buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(seed);
            buf.putFloat(spawnX);
            buf.putFloat(spawnZ);
            buf.flip();
            try (FileChannel fc = FileChannel.open(metaPath, CREATE, WRITE, TRUNCATE_EXISTING)) {
                fc.write(buf);
            }
        } catch (IOException e) {
            System.err.println("[ChunkSerializer] Failed to save world meta: " + e.getMessage());
        }
    }

    /**
     * Load the world seed from {@code saves/<name>/world.dat}.
     *
     * @return the seed, or 0 if the file doesn't exist
     */
    public long loadWorldSeed() {
        Path metaPath = chunksDir.getParent().resolve("world.dat");
        if (!Files.exists(metaPath)) return 0L;
        try (FileChannel fc = FileChannel.open(metaPath, READ)) {
            ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            fc.read(buf);
            buf.flip();
            return buf.getLong();
        } catch (IOException e) {
            System.err.println("[ChunkSerializer] Failed to load world seed: " + e.getMessage());
            return 0L;
        }
    }

    // ---- Private helpers --------------------------------------------------

    private Path chunkPath(ChunkCoord coord) {
        return chunksDir.resolve(coord.cx + "_" + coord.cz + ".dat");
    }
}
