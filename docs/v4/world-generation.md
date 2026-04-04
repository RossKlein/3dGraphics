# v4 World Generation

## Overview

The world is too large to fit in memory or GPU at once. It is divided into **chunks** — fixed-size square regions of the world. Only chunks near the camera are loaded. As the camera moves, new chunks are generated and old ones are unloaded.

Chunk generation is a CPU-heavy task that fits naturally into the existing job system: each chunk generates on a worker thread, then uploads its mesh to the GPU on the render thread.

Two tracks are planned. They share the same chunk lifecycle and differ only in what data a chunk holds and how its mesh is built.

---

## Chunk Fundamentals

### Chunk Coordinates

The world is addressed in chunk coordinates `(cx, cz)`. A chunk at `(cx, cz)` covers world-space `x ∈ [cx * CHUNK_SIZE, (cx+1) * CHUNK_SIZE)` and similarly for `z`. `y` is determined by terrain height (heightmap track) or bounded (voxel track).

`CHUNK_SIZE` is a tunable constant — 16 and 32 are common choices.

### Chunk Lifecycle

```
UNLOADED
   │  camera enters load radius
   ▼
QUEUED      ← added to generation job queue
   │  worker thread picks it up
   ▼
GENERATING  ← heightmap computed, mesh arrays built (CPU, off main thread)
   │  generation complete
   ▼
UPLOADING   ← ModelBuilder.buildModel() called (must happen on GL thread)
   │  upload complete
   ▼
LOADED      ← Entity created, chunk renders each frame
   │  camera exits unload radius
   ▼
UNLOADED    ← VAO/VBO freed, Entity removed
```

### `World`

```java
class World {
    HashMap<Long, Chunk> chunks;        // key = packed (cx, cz)
    HeightmapGenerator heightmapGen;

    void update(Vec3f cameraPos);       // load/unload chunks based on camera
    void render(Renderer renderer);     // submit loaded chunk entities
    Chunk getChunk(int cx, int cz);
}
```

`World.update()` runs each frame as an update job. It computes which chunks should be loaded, queues generation jobs for missing ones, and unloads distant chunks.

---

## Track A — Heightmap Terrain

### Concept

A heightmap is a 2D array of float values representing ground elevation. A terrain chunk is a grid mesh where each vertex's Y coordinate comes from the heightmap.

### `HeightmapGenerator`

Uses layered noise (fractal Brownian motion) to produce a `float[][]` for any `(cx, cz)`. Noise is deterministic from a world seed so chunks can be regenerated identically.

```
amplitude = 40.0
frequency = 0.005
octaves = 6
persistence = 0.5     // how much each octave contributes
lacunarity = 2.0      // how much frequency increases per octave
```

Simplex noise is preferred over Perlin for terrain — fewer directional artifacts.

### `ChunkMesh` (heightmap variant)

Given a `float[][]` heightmap of size `(N+1) x (N+1)`:
- Produces an `N x N` grid of quads (2 triangles each)
- Vertex count: `(N+1)²`
- Index count: `N² * 6`
- Normals computed per vertex by averaging surrounding triangle normals

Color can be assigned by elevation bands:
```
y < waterLevel    → sandy/blue
y < grassLevel    → green
y < rockLevel     → grey
y >= rockLevel    → white (snow)
```

Or by slope (flat = grass, steep = rock).

### Seams

Adjacent chunks share edge vertices (same heightmap values due to deterministic noise), so there are no visible cracks between chunks.

---

## Track B — Voxel World

### Concept

Each chunk is a 3D array of block IDs. Only the visible faces of blocks are added to the mesh (face culling). Adjacent same-type blocks can be merged into larger quads (greedy meshing) to reduce vertex count.

### `VoxelChunk`

```java
class VoxelChunk extends Chunk {
    byte[][][] blocks;   // [x][y][z], value = block type ID
    int width, height, depth;

    void set(int x, int y, int z, byte type);
    byte get(int x, int y, int z);
}
```

### Block Types

Start simple:

| ID | Name | Color |
|----|------|-------|
| 0  | Air  | (skip) |
| 1  | Grass | green |
| 2  | Dirt | brown |
| 3  | Stone | grey |
| 4  | Water | blue |

### Mesh Generation

Simple face-culling approach first (greedy meshing is an optimization for later):

For each block that is not air:
- Check each of its 6 faces
- If the adjacent block is air (or chunk boundary), emit a quad for that face
- Assign normal based on face direction
- Assign color from block type

This runs on a worker thread as a `Job`. The resulting `MeshData` is passed to `ModelBuilder` on the GL thread.

### World Generation for Voxels

1. Generate a heightmap for the chunk column (same `HeightmapGenerator` as Track A)
2. For each `(x, z)` column, fill blocks:
   - `y > height`: Air
   - `y == height`: Grass
   - `y >= height - 3`: Dirt
   - `y < height - 3`: Stone
3. Optional: carve caves using 3D noise
4. Optional: place water at `y < waterLevel`

---

## Job System Integration

Chunk generation is an ideal fit for the existing job system.

### Generation Job

```java
class ChunkGenerationJob extends Job {
    Chunk chunk;

    @Override
    void code() {
        // runs on worker thread
        float[][] heightmap = world.heightmapGen.generate(chunk.cx, chunk.cz);
        MeshData mesh = ChunkMesh.build(heightmap);
        chunk.pendingMesh = mesh;
        chunk.state = GENERATED;
    }

    @Override
    void postCode() {
        // runs after code() completes, back on render thread
        chunk.model = ModelBuilder.buildModel(chunk.pendingMesh);
        chunk.entity = new Entity(chunk.model, chunk.transform);
        chunk.state = LOADED;
    }
}
```

`postCode()` already exists in the `Job` class for exactly this pattern — do CPU work in `code()`, finalize on the main/render thread in `postCode()`.

### Parallelism

Multiple chunks can generate simultaneously since they are independent. The work-stealing pool will naturally distribute them across threads. A generation radius of 5 chunks means up to 100 chunks could generate in parallel when first loading a scene.

---

## Rendering

### Per-Chunk Entity

Each loaded chunk is a `Model` wrapped in an `Entity`. The entity's `Transform` positions it at `(cx * CHUNK_SIZE, 0, cz * CHUNK_SIZE)` in world space. The existing renderer draws it like any other entity.

### Level of Detail (LOD) — Future

Distant chunks can use lower-resolution meshes:
- `LOD_0`: Full resolution (nearby)
- `LOD_1`: Half resolution (mid distance)
- `LOD_2`: Quarter resolution (far)

Not needed for initial implementation.

### Terrain Shader

The existing `StaticShader` works for terrain but a dedicated `TerrainShader` could add:
- Multi-texture blending by height/slope
- Fog based on distance
- Better normal handling for flat terrain faces

---

## Open Questions

- [ ] Chunk size: 16 (Minecraft default) or 32? Affects generation cost vs. draw call count.
- [ ] Load/unload radius: how many chunks in each direction? (3 = 7x7 = 49 chunks)
- [ ] Heightmap track only, voxel track only, or both? (Both share most infrastructure)
- [ ] Greedy meshing for voxels: necessary for performance or premature optimization?
- [ ] Water: transparent plane above voxel water blocks? Separate render pass?
- [ ] Infinite world or bounded map?
- [ ] Should caves be in scope for initial version?
