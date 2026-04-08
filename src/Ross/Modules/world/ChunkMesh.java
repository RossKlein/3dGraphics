package Ross.Modules.world;

/**
 * Converts a heightmap into {@link MeshData} arrays for each LOD level.
 *
 * <h3>Coordinate convention</h3>
 * Vertices are in <em>chunk-local</em> space: x ∈ [0, CHUNK_SIZE],
 * z ∈ [0, CHUNK_SIZE], y = heightmap sample.
 * The chunk's world position is applied by the model matrix at render time
 * ({@code T(cx * CHUNK_SIZE, 0, cz * CHUNK_SIZE)}).
 *
 * <h3>LOD levels</h3>
 * The heightmap is always full-resolution (65×65 vertices, 64×64 quads).
 * Lower LODs sub-sample it at increasing strides:
 *
 * <pre>
 *  LOD | Grid size | Stride | Vertices | Triangles
 *  ----+-----------+--------+----------+----------
 *   0  |  65×65    |   1    |  4 225   |  8 192
 *   1  |  33×33    |   2    |  1 089   |  2 048
 *   2  |  17×17    |   4    |    289   |    512
 *   3  |   9×9     |   8    |     81   |    128
 *   4  |   5×5     |  16    |     25   |     32
 * </pre>
 *
 * <h3>Normals</h3>
 * Per-vertex smooth normals computed by averaging the cross-products of the
 * four surrounding edge pairs. Clamped at boundaries. This gives smooth
 * Phong shading on terrain without separate normal-map passes.
 *
 * <h3>Vertex colors</h3>
 * Driven by {@link BiomeMap#vertexColor} using height, slope, and a
 * deterministic per-vertex noise value derived from the vertex's grid index.
 * No texture coordinates are generated — vertex color carries all visual info.
 */
public final class ChunkMesh {

    private ChunkMesh() { }

    /**
     * Builds mesh data for all {@link World#LOD_COUNT} LOD levels in one
     * call (while the heightmap and humidity arrays are hot in CPU cache).
     *
     * @param heightmap  float[x][z], World.HEIGHTMAP_SIZE × World.HEIGHTMAP_SIZE
     * @param humidityMap float[x][z], same dimensions
     * @return MeshData[LOD_COUNT], index 0 = highest detail
     */
    public static MeshData[] buildAllLods(float[][] heightmap, float[][] humidityMap) {
        MeshData[] result = new MeshData[World.LOD_COUNT];
        for (int lod = 0; lod < World.LOD_COUNT; lod++) {
            result[lod] = build(heightmap, humidityMap, lod);
        }
        return result;
    }

    /**
     * Builds mesh data for a single LOD level.
     * Same as {@link #buildAllLods} but for one level only.
     *
     * @param lod 0 = full resolution … 4 = coarsest
     */
    public static MeshData buildLod(int lod, float[][] heightmap, float[][] humidityMap) {
        return build(heightmap, humidityMap, lod);
    }

    /** Package-private implementation shared by {@link #buildAllLods} and {@link #buildLod}. */
    static MeshData build(float[][] heightmap, float[][] humidityMap, int lod) {
        int gridVerts = World.LOD_GRID_SIZES[lod];   // e.g. 65 for LOD 0
        int gridQuads = gridVerts - 1;                // e.g. 64
        int stride    = 1 << lod;                     // sub-sample stride into heightmap
        float step    = World.CHUNK_SIZE / gridQuads; // world units per quad edge

        int vertCount = gridVerts * gridVerts;
        int triCount  = gridQuads * gridQuads * 2;

        // Skirts: a vertical "curtain" hangs from each of the 4 edges so that
        // T-junction cracks between adjacent LOD levels and floating-point seams
        // are never visible.
        //
        //   skirtVertCount  = 4 edges × gridVerts verts each
        //   skirtTriCount   = 4 edges × gridQuads quads each × 2 tris per quad
        //
        // The skirt depth scales with the quad size so it is always at least
        // one full quad deep, which is more than enough to cover any height
        // mismatch at a LOD seam.
        int   skirtVertCount = 4 * gridVerts;
        int   skirtTriCount  = 4 * gridQuads * 2;
        float skirtDepth     = step * 4f;   // 4× the quad size — plenty for any seam

        int totalVerts   = vertCount + skirtVertCount;
        int totalTris    = triCount  + skirtTriCount;

        float[] vertices = new float[totalVerts * 3];
        float[] normals  = new float[totalVerts * 3];
        float[] colors   = new float[totalVerts * 4];
        int[]   indices  = new int  [totalTris  * 3];

        // ---- Pass 1: positions --------------------------------------------
        for (int gx = 0; gx < gridVerts; gx++) {
            for (int gz = 0; gz < gridVerts; gz++) {
                int hx = gx * stride;   // index into full-resolution heightmap
                int hz = gz * stride;
                hx = Math.min(hx, World.HEIGHTMAP_SIZE - 1);
                hz = Math.min(hz, World.HEIGHTMAP_SIZE - 1);

                float x = gx * step;
                float y = heightmap[hx][hz];
                float z = gz * step;

                int vi = (gx * gridVerts + gz) * 3;
                vertices[vi    ] = x;
                vertices[vi + 1] = y;
                vertices[vi + 2] = z;
            }
        }

        // ---- Pass 2: normals (after positions are filled) -----------------
        computeNormals(vertices, normals, gridVerts);

        // ---- Pass 3: vertex colors ----------------------------------------
        for (int gx = 0; gx < gridVerts; gx++) {
            for (int gz = 0; gz < gridVerts; gz++) {
                int hx = Math.min(gx * stride, World.HEIGHTMAP_SIZE - 1);
                int hz = Math.min(gz * stride, World.HEIGHTMAP_SIZE - 1);

                float height   = heightmap[hx][hz];
                float humidity = humidityMap[hx][hz];

                float ny    = normals[(gx * gridVerts + gz) * 3 + 1];
                float slope = 1f - ny;  // 0 = flat face (ny=1), 1 = vertical (ny=0)

                // Cheap deterministic per-vertex variation (no extra noise call)
                float noiseVal = fract((float) Math.sin(gx * 127.1f + gz * 311.7f) * 43758.5f);

                BiomeMap.Biome biome = BiomeMap.biomeAt(height, humidity);
                int ci = (gx * gridVerts + gz) * 4;
                BiomeMap.vertexColor(biome, height, slope, noiseVal, colors, ci);
            }
        }

        // ---- Pass 4: surface indices (CCW winding) ------------------------
        int idx = 0;
        for (int gx = 0; gx < gridQuads; gx++) {
            for (int gz = 0; gz < gridQuads; gz++) {
                int v0 = gx       * gridVerts + gz;
                int v1 = gx       * gridVerts + gz + 1;
                int v2 = (gx + 1) * gridVerts + gz;
                int v3 = (gx + 1) * gridVerts + gz + 1;

                // Triangle 1
                indices[idx++] = v0;
                indices[idx++] = v1;
                indices[idx++] = v2;

                // Triangle 2
                indices[idx++] = v1;
                indices[idx++] = v3;
                indices[idx++] = v2;
            }
        }

        // ---- Pass 5: skirt geometry ----------------------------------------
        // One skirt vertex per edge vertex: same X/Z, Y pushed down by skirtDepth.
        // The skirt wall triangles connect the edge surface row to the row below,
        // filling any crack that would otherwise be visible between adjacent chunks.
        //
        // Skirt vertex layout (appended after main grid):
        //   [vertCount + 0              .. + gridVerts-1]  → bottom edge (gz = 0)
        //   [vertCount + gridVerts      .. + 2*gridVerts-1] → top edge   (gz = gridVerts-1)
        //   [vertCount + 2*gridVerts    .. + 3*gridVerts-1] → left edge  (gx = 0)
        //   [vertCount + 3*gridVerts    .. + 4*gridVerts-1] → right edge (gx = gridVerts-1)
        int botBase   = vertCount;
        int topBase   = vertCount +     gridVerts;
        int leftBase  = vertCount + 2 * gridVerts;
        int rightBase = vertCount + 3 * gridVerts;

        // Helper: write one skirt vertex at sv, copying color from mainIdx, pushing Y down.
        // Normals for skirt verts point straight down (they are never lit prominently).
        for (int gx = 0; gx < gridVerts; gx++) {
            // Bottom (gz=0)
            int mainIdx = gx * gridVerts + 0;
            int sv      = botBase + gx;
            vertices[sv*3    ] = vertices[mainIdx*3    ];
            vertices[sv*3 + 1] = vertices[mainIdx*3 + 1] - skirtDepth;
            vertices[sv*3 + 2] = vertices[mainIdx*3 + 2];
            System.arraycopy(colors, mainIdx*4, colors, sv*4, 4);
            normals[sv*3 + 1] = -1f;  // point down

            // Top (gz=gridVerts-1)
            mainIdx = gx * gridVerts + (gridVerts - 1);
            sv      = topBase + gx;
            vertices[sv*3    ] = vertices[mainIdx*3    ];
            vertices[sv*3 + 1] = vertices[mainIdx*3 + 1] - skirtDepth;
            vertices[sv*3 + 2] = vertices[mainIdx*3 + 2];
            System.arraycopy(colors, mainIdx*4, colors, sv*4, 4);
            normals[sv*3 + 1] = -1f;
        }
        for (int gz = 0; gz < gridVerts; gz++) {
            // Left (gx=0)
            int mainIdx = 0 * gridVerts + gz;
            int sv      = leftBase + gz;
            vertices[sv*3    ] = vertices[mainIdx*3    ];
            vertices[sv*3 + 1] = vertices[mainIdx*3 + 1] - skirtDepth;
            vertices[sv*3 + 2] = vertices[mainIdx*3 + 2];
            System.arraycopy(colors, mainIdx*4, colors, sv*4, 4);
            normals[sv*3 + 1] = -1f;

            // Right (gx=gridVerts-1)
            mainIdx = (gridVerts - 1) * gridVerts + gz;
            sv      = rightBase + gz;
            vertices[sv*3    ] = vertices[mainIdx*3    ];
            vertices[sv*3 + 1] = vertices[mainIdx*3 + 1] - skirtDepth;
            vertices[sv*3 + 2] = vertices[mainIdx*3 + 2];
            System.arraycopy(colors, mainIdx*4, colors, sv*4, 4);
            normals[sv*3 + 1] = -1f;
        }

        // Skirt indices: each quad is (edge vertex i, edge vertex i+1, skirt i+1, skirt i).
        // Using the same CCW convention as the surface so the outer face is the front face.
        for (int i = 0; i < gridQuads; i++) {
            // Bottom edge (faces outward toward -Z)
            int e0 =  i      * gridVerts;   int e1 = (i + 1) * gridVerts;
            int s0 = botBase + i;           int s1 = botBase + i + 1;
            indices[idx++] = e0; indices[idx++] = s1; indices[idx++] = s0;
            indices[idx++] = e0; indices[idx++] = e1; indices[idx++] = s1;

            // Top edge (faces outward toward +Z)
            e0 =  i      * gridVerts + (gridVerts - 1);
            e1 = (i + 1) * gridVerts + (gridVerts - 1);
            s0 = topBase + i;    s1 = topBase + i + 1;
            indices[idx++] = e0; indices[idx++] = s1; indices[idx++] = e1;
            indices[idx++] = e0; indices[idx++] = s0; indices[idx++] = s1;

            // Left edge (faces outward toward -X)
            e0 = i;       e1 = i + 1;
            s0 = leftBase + i;   s1 = leftBase + i + 1;
            indices[idx++] = e0; indices[idx++] = s1; indices[idx++] = e1;
            indices[idx++] = e0; indices[idx++] = s0; indices[idx++] = s1;

            // Right edge (faces outward toward +X)
            e0 = (gridVerts - 1) * gridVerts + i;
            e1 = (gridVerts - 1) * gridVerts + i + 1;
            s0 = rightBase + i;  s1 = rightBase + i + 1;
            indices[idx++] = e0; indices[idx++] = s1; indices[idx++] = s0;
            indices[idx++] = e0; indices[idx++] = e1; indices[idx++] = s1;
        }

        return new MeshData(vertices, colors, normals, indices);
    }

    // ---- Normal computation -----------------------------------------------

    /**
     * Fills {@code normals[]} with smooth per-vertex normals by averaging
     * the cross-products of adjacent edge pairs.
     *
     * For interior vertex (gx, gz):
     *   tangent_x = P(gx+1,gz) − P(gx-1,gz)
     *   tangent_z = P(gx,gz+1) − P(gx,gz-1)
     *   normal    = normalize(cross(tangent_x, tangent_z))
     * Boundary vertices clamp their neighbour indices.
     */
    private static void computeNormals(float[] verts, float[] normals, int gridVerts) {
        for (int gx = 0; gx < gridVerts; gx++) {
            for (int gz = 0; gz < gridVerts; gz++) {

                // Clamped neighbour grid coords
                int gxL = Math.max(gx - 1, 0);
                int gxR = Math.min(gx + 1, gridVerts - 1);
                int gzB = Math.max(gz - 1, 0);
                int gzF = Math.min(gz + 1, gridVerts - 1);

                // Direct index access — avoids allocating float[3] per neighbour.
                // Previously vertex() returned new float[]{...}, causing ~16 900
                // short-lived allocations per LOD-0 mesh and severe GC pressure.
                int iL = (gxL * gridVerts + gz ) * 3;
                int iR = (gxR * gridVerts + gz ) * 3;
                int iB = (gx  * gridVerts + gzB) * 3;
                int iF = (gx  * gridVerts + gzF) * 3;

                float tx = verts[iR]   - verts[iL];
                float ty = verts[iR+1] - verts[iL+1];
                float tz = verts[iR+2] - verts[iL+2];

                float bx = verts[iF]   - verts[iB];
                float by = verts[iF+1] - verts[iB+1];
                float bz = verts[iF+2] - verts[iB+2];

                // Cross product: B × T  (right-handed, Y up for a flat surface)
                float nx = by * tz - bz * ty;
                float ny = bz * tx - bx * tz;
                float nz = bx * ty - by * tx;

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
                else             { nx = 0; ny = 1; nz = 0; }

                int ni = (gx * gridVerts + gz) * 3;
                normals[ni    ] = nx;
                normals[ni + 1] = ny;
                normals[ni + 2] = nz;
            }
        }
    }

    // ---- Utilities --------------------------------------------------------

    /** Fractional part of a float — used for cheap pseudo-noise. */
    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }
}
