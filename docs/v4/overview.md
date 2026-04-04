# lqjgl Graphics Engine — v4 Planning Overview

## Goal

Build a procedurally generated world on top of the v3 engine. The world should support:
- Heightmap-based terrain (rolling hills, mountains, rivers)
- Voxel-style blocks (Minecraft-style, optional track)
- Authored OBJ assets placed in the world (trees, rocks, props)
- Efficient rendering of large amounts of geometry

This requires two main additions to v3: a **model/entity system** and a **world generation system**. These are documented separately and link back here.

---

## What v3 Gets Right (Keep It)

| System | Status |
|--------|--------|
| Job system (work-stealing, subtasks, duration sorting) | Keep as-is |
| Quaternion camera with dual-mode controls | Keep as-is |
| Flamegraph profiler | Keep as-is |
| `ModelBuilder` (raw arrays → GPU VAO/VBO) | Keep as the GPU upload layer |
| `OBJLoader` | Keep for prop loading |
| Phong lighting in shader | Keep, extend |
| Render/Update thread separation | Keep as-is |

---

## What v3 Is Missing

### 1. Transform / Entity System
v3 scenes manage a single model and its matrix by hand. There is no way to have 500 trees each with their own position without writing 500 lines of boilerplate. A lightweight `Transform` + `Entity` layer solves this.

→ See [model-system.md](model-system.md)

### 2. Procedural Mesh Generation
v3 can only get geometry from OBJ files. Procedural worlds need geometry generated at runtime from noise, heightmaps, or block data. A set of `MeshBuilder` utilities produces float arrays that feed directly into the existing `ModelBuilder`.

→ See [world-generation.md](world-generation.md)

### 3. Chunk Management
The world is too large to hold in GPU memory at once. It must be divided into chunks that are loaded, generated, and unloaded as the camera moves. Chunk generation jobs fit naturally into the existing job system.

→ See [world-generation.md](world-generation.md)

### 4. Instanced Rendering
Placing 1000 trees as 1000 separate draw calls is too slow. Instanced rendering sends one draw call with per-instance transform data, cutting CPU overhead dramatically.

→ See [model-system.md](model-system.md)

---

## v4 Package Layout (Proposed)

```
Ross/
  Instance/
    Main.java              (unchanged)
    WorldScene.java        (new scene replacing testscene for the world)
  Modules/
    Engine.java            (unchanged)
    JobModule.java         (unchanged)
    JobQueue.java          (unchanged)
    Job.java               (unchanged)
    Renderer.java          (extended for entity batching)
    Window.java            (unchanged)
    math/                  (unchanged)
    input/                 (unchanged)
    flamegraph/            (unchanged)
    shaders/
      Shader.java          (unchanged)
      StaticShader.java    (unchanged)
      TerrainShader.java   (new)
    models/
      Model.java           (unchanged)
      TexturedModel.java   (unchanged)
      ModelBuilder.java    (unchanged)
      OBJLoader.java       (unchanged)
      Entity.java          (NEW — model + transform)
      Transform.java       (NEW — position/rotation/scale → matrix)
      MeshBuilder.java     (NEW — procedural geometry utilities)
      InstancedModel.java  (NEW — model with per-instance VBO)
    scene/
      Scene.java           (unchanged)
      Utils.java           (extended)
    world/
      World.java           (NEW — owns chunk map, manages lifecycle)
      Chunk.java           (NEW — fixed-size region of world data)
      ChunkMesh.java       (NEW — generates terrain mesh from heightmap)
      VoxelChunk.java      (NEW — optional: voxel block data + mesh gen)
      HeightmapGenerator.java (NEW — noise-based heightmap)
      NoiseUtil.java       (NEW — Perlin/Simplex noise)
```

---

## Development Tracks

Two parallel tracks can be developed independently:

**Track A — Heightmap Terrain**
Dense continuous surface mesh. Good for natural landscapes. Simpler rendering, harder to make interactive/destructible.

**Track B — Voxel World**
Discrete blocks. More complex mesh generation (greedy meshing). Naturally destructible and interactive. Higher memory overhead per chunk.

Both tracks share the same chunk lifecycle, job system integration, and entity/prop system. They differ only in what `Chunk` contains and how `ChunkMesh` generates geometry.

---

## Open Questions

- [ ] Terrain only, voxel only, or both?
- [ ] What scale? (small demo scene vs. infinite world)
- [ ] Biomes / multiple terrain types?
- [ ] Physics / collision, or purely visual?
- [ ] Day/night cycle, dynamic lighting?
