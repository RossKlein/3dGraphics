package Ross.Modules.world;

/**
 * CPU-side mesh arrays produced by {@link ChunkMesh#build}.
 *
 * Handed to {@code ModelBuilder.buildModel()} on the GL thread, then the
 * reference should be nulled so the GC can reclaim the arrays (~17 MB for a
 * full LOD-0 chunk at 65×65 verts).
 *
 * Attribute layout (matches ModelBuilder slot assignments):
 *   slot 0 — position : 3 floats per vertex  (x, y, z)
 *   slot 1 — color    : 4 floats per vertex  (r, g, b, a)
 *   slot 2 — normal   : 3 floats per vertex  (nx, ny, nz)
 */
public final class MeshData {

    public final float[] vertices;   // 3 floats / vertex
    public final float[] colors;     // 4 floats / vertex
    public final float[] normals;    // 3 floats / vertex
    public final int[]   indices;    // 3 ints / triangle

    public MeshData(float[] vertices, float[] colors, float[] normals, int[] indices) {
        this.vertices = vertices;
        this.colors   = colors;
        this.normals  = normals;
        this.indices  = indices;
    }

    /** Number of vertices in this mesh. */
    public int vertexCount() {
        return vertices.length / 3;
    }

    /** Number of triangles. */
    public int triangleCount() {
        return indices.length / 3;
    }

    /** Approximate heap cost in bytes (CPU side only). */
    public long heapBytes() {
        return (long) vertices.length * 4
             + (long) colors.length   * 4
             + (long) normals.length  * 4
             + (long) indices.length  * 4;
    }
}
