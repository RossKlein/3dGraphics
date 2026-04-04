# v4 World Generation

## Design Philosophy

The world is seen from above and at speed. This changes everything about how terrain must be designed:

- **Large view distance** — you can see many kilometers of terrain simultaneously
- **Fast chunk crossing** — flying at speed means crossing chunk boundaries much faster than walking
- **Top-down visibility** — terrain must look good from above (texture/color variety), not just at the edges/silhouette
- **Atmosphere as performance** — exponential haze limits effective render distance, which is also our LOD cover

---

## Heightmap Terrain

### Noise Stack

The heightmap is built from multiple noise layers combined (fractal Brownian motion):

```
base continent shape   → very low frequency, high amplitude (where is land vs sea)
mountain ridges        → medium frequency, medium amplitude
rolling hills          → medium-high frequency, low amplitude
surface detail         → high frequency, very low amplitude (for normal map variation)
```

A separate **humidity map** (different noise seed, same technique) combined with the height map determines biome assignment. This is the same approach used by most terrain generators (Minecraft included, just in 2D).

### Biome Assignment

```
height < seaLevel                         → Ocean
height < beachLine                        → Beach / Coast
height >= beachLine, humidity < 0.3       → Dry plains / Savanna
height >= beachLine, humidity >= 0.3      → Grassland / Forest
height >= hillLine                        → Hills
height >= mountainLine                    → Mountains / Rock
height >= alpineLine                      → Alpine / Snow
```

Biome determines vertex color palette and which assets get placed (trees, rocks, etc.).

### Chunk Size and Resolution

- **Chunk size**: 64 world units × 64 world units (tunable)
- **LOD 0 grid**: 64×64 quads (65×65 vertices) — full resolution
- **LOD 1**: 32×32 quads
- **LOD 2**: 16×16 quads
- **LOD 3**: 8×8 quads
- **LOD 4**: 4×4 quads — just the silhouette, hidden by haze

Vertices are shared at chunk edges for the same LOD level. LOD seams (different LOD adjacent chunks) are hidden by haze at the distances where they occur.

---

## Chunk Lifecycle

```
UNLOADED
   │  enters load radius
   ▼
QUEUED (priority = distance from camera)
   │  worker picks it up
   ▼
GENERATING (CPU: noise → heightmap → mesh arrays for all LOD levels)
   │  can be CANCELLED if chunk exits range while generating
   ▼
PENDING_UPLOAD (waiting for GL thread slot)
   │  GL thread uploads VAOs
   ▼
LOADED (renders each frame at appropriate LOD)
   │  exits unload radius
   ▼
UNLOADED (VAOs freed)
```

### Load/Unload Radii

Two separate radii prevent thrashing (chunk loading and unloading on every frame near the boundary):

- **Load radius**: 12 chunks — start generating anything inside this
- **Unload radius**: 15 chunks — free anything outside this

### Predictive Loading

Because the camera moves fast, we also load chunks in the **flight direction** beyond the normal load radius:

```
predictiveChunks = camera.velocity.normalize() * PREDICTIVE_LOOK_AHEAD
```

Chunks along the flight vector get priority boost even if they are outside normal load distance.

---

## LOD System

### LOD Selection

Each visible chunk gets an LOD level each frame based on distance from camera:

| Distance | LOD Level | Grid |
|----------|-----------|------|
| 0–3 chunks | LOD 0 | 64×64 |
| 3–6 chunks | LOD 1 | 32×32 |
| 6–10 chunks | LOD 2 | 16×16 |
| 10–15 chunks | LOD 3 | 8×8 |
| 15–20 chunks | LOD 4 | 4×4 |
| >20 chunks | hidden | — |

All LOD levels are generated once when a chunk first loads (on the worker thread). Switching LOD is just switching which VAO to bind — no regeneration.

### LOD Transitions and Haze

The haze distance (fog density) is tuned so that LOD transitions happen inside the haze zone. Specifically, the LOD 1→2 transition occurs at the distance where haze reduces visibility to ~60%. The pop is invisible.

This is the same technique Subnautica uses — the "murk" distance is tuned to match the LOD levels.

---

## Asset Placement

Each chunk, once its heightmap is generated, also runs an **asset placement pass** to determine where props go (trees, rocks, etc.). This runs on the same worker thread as mesh generation.

### Placement Algorithm

For each asset type in the chunk's biome:
1. Generate a Poisson disk sample set (evenly spaced, no clustering) using chunk coordinates as seed
2. For each sample point, query heightmap height and slope
3. Place asset if: biome matches, slope is under threshold, height is in range
4. Store as a list of `(assetType, worldPosition, rotation, scale)` tuples

The tuples are handed to `AssetPlacer` which adds them to the appropriate `InstancedModel` buffer.

### Asset LOD

Assets also have LOD levels:

| Distance | Trees | Rocks |
|----------|-------|-------|
| 0–4 chunks | Full mesh | Full mesh |
| 4–8 chunks | Reduced mesh | Reduced mesh |
| 8–12 chunks | Billboard (flat quad) | Billboard |
| >12 chunks | Hidden | Hidden |

Billboards are camera-facing quads with a pre-rendered or simple texture. They cost almost nothing to render and are invisible at distance.

---

## Mesh Generation Details

### `ChunkMesh.build(float[][] heightmap, int lodLevel)`

Returns `MeshData` (vertices, indices, normals, colors).

**Vertices**: sample heightmap at LOD resolution steps. `vertex.y = heightmap[x][z]`.

**Normals**: per-vertex normal computed by averaging the cross products of surrounding triangles. Smooth normals on terrain catch lighting well.

**Colors (vertex color)**: assigned by biome + local variation:
- Sample a small noise value per vertex for natural variation
- Blend between two biome colors based on noise
- Add slope-based darkening (steep faces are rockier/darker)
- Snow blending above `alpineLine`

No texture coordinates needed for basic terrain — vertex color carries all the visual information. This is cheaper and avoids tiling seams.

### Water

Water is a **separate flat mesh** at `y = seaLevel`. It is not part of the terrain chunk — it is a global plane clipped to loaded chunk area. The water shader handles reflections, animated ripples, and shore foam. Rendered in a separate pass after opaque terrain.

---

## Job System Integration

### Generation Job

```java
class ChunkGenerateJob extends Job {
    Chunk chunk;
    CancelToken cancel;

    void code() {
        if (cancel.isCancelled()) return;
        float[][] heightmap = heightmapGen.generate(chunk.cx, chunk.cz);
        if (cancel.isCancelled()) return;
        for (int lod = 0; lod < 5; lod++) {
            chunk.meshData[lod] = ChunkMesh.build(heightmap, lod);
            if (cancel.isCancelled()) return;
        }
        chunk.assetList = assetPlacer.place(chunk.cx, chunk.cz, heightmap, chunk.biome);
        chunk.state = PENDING_UPLOAD;
    }
}
```

### Upload Job (GL thread only)

```java
class ChunkUploadJob extends Job {
    Chunk chunk;

    void code() {
        // Runs on render thread (GL context required)
        for (int lod = 0; lod < 5; lod++) {
            chunk.models[lod] = ModelBuilder.buildModel(chunk.meshData[lod]);
        }
        chunk.meshData = null; // free CPU-side arrays
        chunk.state = LOADED;
    }
}
```

### Priority Tiers

Generation jobs are assigned priority based on distance:
- `PRIORITY_HIGH`: 0–4 chunks (visible near-field, generate immediately)
- `PRIORITY_NORMAL`: 4–8 chunks (load-ahead)
- `PRIORITY_LOW`: 8–12 chunks (prefetch)
- `PRIORITY_IDLE`: predictive look-ahead

→ See [job-system.md](job-system.md) for how priority is implemented.

---

## Open Questions

- [ ] Chunk size: 64 feels right for flight speed — confirm with playtesting
- [ ] How many LOD levels to pre-generate vs. generate on demand?
- [ ] Seam handling at chunk edges for different LOD levels — need T-junction fix or rely on haze?
- [ ] Cave systems? (underground, not visible while flying — probably out of scope initially)
- [ ] Rivers? (follow low-altitude noise valleys, special water shader)
- [ ] Biome blending at borders? (currently hard cutoff — noise-based gradient is better)
