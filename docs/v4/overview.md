# lqjgl Engine — v4 Planning Overview

## Goal

Build a bird flight game: an open world aerial experience driven by procedural terrain, beautiful atmosphere-first shaders, and aggressive rendering optimizations. The visual reference is Subnautica — shader tricks and atmosphere making limited geometry look exceptional.

→ See [game-vision.md](game-vision.md) for the full game direction.

---

## What v3 Gets Right (Keep It)

| System | Notes |
|--------|-------|
| Job system (work-stealing, subtasks) | Keep. Extend with priority levels and cancellation. |
| Quaternion camera | Keep. Replace with flight physics camera. |
| Flamegraph profiler | Keep as-is. |
| `ModelBuilder` (arrays → GPU) | Keep as the GPU upload layer. |
| `OBJLoader` | **Replaced** by `AssimpLoader`. Assimp handles OBJ, FBX, GLTF, and 40+ other formats with one API — and adds skeletal animation data. |
| Phong lighting | Starting point. Extend to support dynamic sun direction. |
| Render/Update thread separation | Keep as-is. |

---

## What v4 Adds

### 1. Transform + Entity System
A `Transform` (position/rotation/scale → matrix) paired with a `Model` to form an `Entity`. Scenes manage lists of entities rather than one model by hand.

→ See [model-system.md](model-system.md)

### 2. Entity System + Instanced Rendering
`GameObject` base class unifies all world objects behind a single lifecycle. Creating an object automatically queues its initialization jobs — callers never touch `JobModule` directly. Instanced rendering (`InstanceBatch`) gives one draw call for all trees of a type.

→ See [entity-system.md](entity-system.md) (detailed) and [model-system.md](model-system.md) (quick reference)

### 3. Procedural Terrain + Chunk Streaming
Heightmap-based terrain divided into chunks. Chunks generate on worker threads and stream in/out as the camera moves. Multiple LOD levels per chunk.

→ See [world-generation.md](world-generation.md)

### 4. LOD System
Each chunk and each instanced asset class has multiple detail levels. The active LOD is chosen by distance from camera. Atmosphere haze is tuned to cover LOD transitions.

→ See [world-generation.md](world-generation.md)

### 5. Atmosphere-First Shaders
Sky gradient, atmospheric haze (distance fog), god rays, wind vertex shader, water surface, normal mapping. All are shader effects — no additional geometry or CPU cost.

→ See [shaders.md](shaders.md) *(to be written)*

### 6. Job System — Priority + Cancellation
The existing job scheduler sorts by duration but has no explicit priority levels and no way to cancel in-flight jobs. Flying fast means you can outrun your chunk generation. Needs: priority tiers, cancellation tokens, and a proper dependency graph.

→ See [job-system.md](job-system.md) *(to be written)*

---

## v4 Package Layout (Proposed)

```
Ross/
  Instance/
    Main.java
    BirdScene.java             (new main scene)
  Modules/
    Engine.java                (unchanged)
    JobModule.java             (extended: priority queues)
    JobQueue.java              (extended: priority + cancellation)
    Job.java                   (extended: priority field, cancel token)
    Renderer.java              (extended: entity batching, instanced draw)
    Window.java                (unchanged)
    math/                      (unchanged)
    input/                     (unchanged)
    flamegraph/                (unchanged)
    shaders/
      Shader.java              (unchanged)
      StaticShader.java        (unchanged)
      TerrainShader.java       (NEW)
      SkyShader.java           (NEW)
      WaterShader.java         (NEW)
    models/
      Model.java               (unchanged)
      TexturedModel.java       (unchanged)
      ModelBuilder.java        (unchanged — extended for skinned meshes)
      OBJLoader.java           (REMOVED — replaced by AssimpLoader)
      AssimpLoader.java        (NEW — static and animated asset loading)
      Transform.java           (NEW)
      MeshBuilder.java         (NEW — procedural geometry: terrain, water, sky)
      InstancedModel.java      (NEW — one draw call, many transforms)
      Billboard.java           (NEW — camera-facing quad for distant assets)
    animation/
      Skeleton.java            (NEW — bone hierarchy, offset matrices)
      AnimationClip.java       (NEW — keyframe data for one named animation)
      AnimationController.java (NEW — per-instance playback, cross-fading)
      SkeletonBuilder.java     (NEW — builds Skeleton from AssimpData)
      AnimationClipBuilder.java (NEW — builds clips from AssimpData)
    scene/
      Scene.java               (unchanged)
      Utils.java               (extended)
    world/
      World.java               (NEW — chunk map, lifecycle, streaming)
      Chunk.java               (NEW — one terrain tile)
      ChunkMesh.java           (NEW — heightmap → mesh arrays)
      HeightmapGenerator.java  (NEW — layered noise → float[][])
      BiomeMap.java            (NEW — height + humidity → biome)
      AssetPlacer.java         (NEW — procedural prop placement per chunk)
      LODSelector.java         (NEW — distance → LOD level)
    flight/
      FlightPhysics.java       (NEW — bank/pitch/speed/thermals)
      FlightCamera.java        (NEW — follows bird, lag, tilt)
```

---

## Open Questions

- [ ] What is the player character? A specific bird (eagle, hawk) or abstract?
- [ ] Is there a goal / progression, or pure exploration?
- [ ] Multiplayer ever? (affects architecture significantly)
- [ ] Target FPS: 60 locked, uncapped, or VR-ready 90?
- [ ] What platforms? (Windows only matches current exe release)
