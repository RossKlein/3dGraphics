# v4 Implementation Notes

Engineering observations about the current codebase and what needs to happen before / during
the terrain chunking work. These are cross-cutting notes that don't fit neatly into the
feature-specific docs.

---

## VBO Leak — Fixed

`Model.dispose()` previously only deleted the VAO. Every `ModelBuilder.buildModel()` call
leaked 4 VBOs (EBO + 3 attribute VBOs). At ~4 VBOs × N chunks ever loaded over a session,
this would exhaust the GPU's VBO limit or cause driver-side OOM.

**Fix applied**: `Model` now stores its own `int[] vboIds`. `dispose()` deletes both the VAO
and all VBOs. `ModelBuilder.bindIndices()` and `storeDataInAttributeList()` now return the VBO
id so the caller can collect them.

The static `vboIdList` / `vaoIdList` in `ModelBuilder` are kept for the bulk `exit()` call at
engine shutdown. OpenGL silently ignores deletes of already-freed objects, so models that were
properly disposed during the session are safe to "double-delete" at shutdown.

---

## Critical Gap: GL-Thread Pinning

The job system docs describe `glThread = true` on a `Job` to designate it as render-thread-only
(see [job-system.md](job-system.md)). **This is not implemented yet.**

Without it, chunk mesh upload (`ModelBuilder.buildModel()`) cannot safely happen on a worker
thread — the GL context is not shared. Right now the only safe upload path is inside a layer's
`render()` call (which runs on the GL thread). The current workaround in `TestWorldLayer` and
`SkyLayer` is to set a `glReady` flag and do upload inside `render()` when CPU data is ready.

**For chunk streaming to work robustly**, we need to either:

1. **Minimal path**: `LayerManager` drains a small `ConcurrentLinkedQueue<Runnable> glJobs`
   queue at the top of each render frame. Chunk upload jobs post a lambda to this queue instead
   of running directly. No changes to `Job`/`JobQueue`. Fast to implement.

2. **Full path**: Add `glThread` flag to `Job`, `JobQueue` maintains a separate GL queue, render
   thread drains it as described in job-system.md.

**Recommendation**: Implement option 1 first (one day of work, unblocks chunking immediately).
Option 2 is the right end-state but is a larger refactor — do it when priority / cancellation
are also being added.

---

## Critical Gap: Job Priority + Cancellation

Without cancellation, if the player moves fast, workers generate meshes for chunks that are
no longer in range. The mesh still uploads and occupies a VAO slot, but never renders. At
speed, this wastes all 4 worker threads.

Without priority, a chunk 12 tiles away generates before one 1 tile away — the player sees
blank terrain closest to them.

Both are described in [job-system.md](job-system.md). Neither is implemented.

**Minimum viable for initial terrain**: a `volatile boolean cancelled` field on `Chunk` (not a
full `CancelToken` class yet). Generation jobs check it. Priority can be approximated by
submission order (nearest chunks submitted first each `update()` frame).

---

## What Doesn't Exist Yet

The `world/` package described in [overview.md](overview.md) is entirely missing:

```
Ross/Modules/world/
  World.java              ← chunk map + streaming logic
  Chunk.java              ← state machine + mesh data + VAOs
  ChunkCoord.java         ← (cx, cz) integer key, hashable
  ChunkMesh.java          ← heightmap float[][] → vertices/indices/normals/colors
  HeightmapGenerator.java ← layered noise → float[][]
  BiomeMap.java           ← (height, humidity) → Biome enum
  AssetPlacer.java        ← prop placement per chunk
  LODSelector.java        ← distance → LOD level int
  MeshData.java           ← plain holder: float[] verts/normals/colors, int[] indices
```

`MeshData` doesn't exist anywhere in the codebase but is referenced throughout the docs. It
should be a simple record/POJO with no logic.

---

## Persistence: Probably Not Needed (Yet)

The docs don't explicitly mention saving chunk data. Since terrain generation is deterministic
(same seed + ChunkCoord → same heightmap), chunks don't need to be written to disk. Unloading
a chunk and regenerating it later gives identical results.

Persistence becomes necessary only for:
- Player modifications to terrain (out of scope for M1)
- Placed entity saves (e.g. discovered nests, waypoints)
- Cached generation results for startup speed (premature optimization)

**Decision**: Skip persistence entirely for M1. The only "save" is the world seed (a single
`long` in `GameState` or a config file).

---

## LOD: Build the Structure Now, Render One Level Initially

Generating all 5 LOD levels in the same worker job costs ~5× more CPU time but means we never
need to re-generate. The cost per chunk (all LODs) is dominated by the noise sampling, not the
mesh construction. Generating LOD 0 only and re-generating later is strictly worse.

**Decision**: Generate all 5 LOD levels in one `ChunkGenerateJob`. Upload all 5 VAOs in the
`ChunkUploadJob`. Initially only `models[0]` is used in `render()`. LOD selection is wired in
later by plugging in `LODSelector`.

---

## Memory Estimate: 20-Chunk View Radius

- Load radius: 12 chunks → 25×25 = 625 chunks max in memory
- CPU side (before upload, freed after): LOD 0 mesh ≈ 65×65 verts × (3+4+3) floats × 4 bytes ≈ 655KB.
  All 5 LODs together ≈ 655+164+41+10+3 KB ≈ 873KB per chunk.
  In flight at full radius ≈ 873KB × 625 ≈ 546MB — this is high.
  BUT: meshData is nulled immediately after GPU upload. Only one chunk's CPU arrays exist at
  once while it's being generated. Steady-state CPU cost is near zero.
- GPU VAOs: ~169KB for LOD 0 + ~42KB for all other LODs = ~211KB per chunk.
  625 loaded chunks × 211KB ≈ 128MB VRAM. Fine.

---

## Frustum Culling

Flying means roughly 50% of loaded chunks are always behind the camera. Without frustum
culling, they are sent through the full render pipeline (state changes, draw calls) for nothing.

For a 20-chunk radius, that's ~312 redundant draw calls per frame. Not catastrophic but worth
doing early — it's a few lines per chunk in `TerrainLayer.render()`:

```java
// In TerrainLayer render loop:
if (!frustum.intersectsAABB(chunk.worldBounds())) continue;
```

`Frustum` is a class that extracts the 6 planes from the projection-view matrix and tests
axis-aligned boxes. Standard implementation, ~50 lines.

---

## Suggested Build Order

1. **[Done]** Fix VBO leak
2. `MeshData` POJO
3. `ChunkCoord` — integer key, equals/hashCode, world-offset helper
4. `Chunk` — state enum, fields, simple `cancelled` flag
5. `ChunkMesh.buildFlat()` — flat terrain grid (no noise yet), returns `MeshData[]` for each LOD
6. `TerrainLayer` — WORLD layer, order 1, creates a `HashMap<ChunkCoord, Chunk>` of fixed test chunks
   and renders them. Proves the pipeline: worker generates, GL thread uploads, layer renders.
7. Minimal GL job queue — `ConcurrentLinkedQueue<Runnable>` drained in `LayerManager.renderFrame()`
   before WORLD layers; chunk upload posts here instead of doing lazy-init in `render()`.
8. `ChunkManager` — streaming logic: `update(cameraPos)` computes needed set, schedules loads/unloads
9. `HeightmapGenerator` — simple fBm noise stack (start with 2 octaves, tune later)
10. `LODSelector` — distance table from world-generation.md
11. Frustum culling

Each step is independently testable.

---

## Open Questions Added After Reading Codebase

- **`ModelBuilder` is a stateful instance but `vaoIdList`/`vboIdList` are static.** If two
  `ModelBuilder` instances exist (e.g. one for terrain, one for props), they share the same list.
  `exit()` on either one would delete the other's resources. This is a latent bug. For now it
  doesn't matter (one ModelBuilder used everywhere), but the world system should either use a
  singleton or make the lists instance fields.

- **`StaticShader` currently handles all model rendering.** Terrain needs at minimum a
  `TerrainShader` with fog uniforms (see game-vision.md — atmosphere hides LOD transitions).
  The fog parameters (density, color, far distance) come from `GameState`. Can reuse `StaticShader`
  initially with fog added as a uniform, then split into `TerrainShader` when it diverges enough.

- **`position` in `JobModule` is the camera position in world space (Vec3f).** The chunk manager
  reads this directly to determine which chunks to load. No additional camera abstraction is
  needed for M1.

- **`jobs.modelview` is a pure rotation matrix** (no translation — see `matrixWork` job in
  `JobModule`). The terrain chunk model matrix must translate each chunk to its world position.
  Per-chunk model matrix = `T(cx * 64, 0, cz * 64)`. This is the `transform()` call in
  `TerrainChunk.render()` from entity-system.md.
