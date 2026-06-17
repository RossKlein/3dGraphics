# v4 Entity System

## Core Principle

**Game code never creates jobs directly.** Jobs are an implementation detail of the engine. When you add an object to the world, the object knows what jobs it needs — you just add it.

```java
world.add(new TerrainChunk(3, 7));      // automatically queues: generate → upload
world.add(new Prop("oak_tree.obj", t)); // automatically queues: load → upload (or hits cache)
world.add(new Bird(startPos));          // automatically queues: load → upload, then runs physics each frame
```

The caller doesn't touch `JobModule`, `ModelBuilder`, or `AssimpLoader`. Those are wiring.

---

## `GameObject` — The Base

Every object in the world extends `GameObject`. It owns a lifecycle state and declares what happens when it enters or leaves the world.

```java
abstract class GameObject {

    enum State {
        UNINITIALIZED,    // just constructed, not yet added to world
        LOADING,          // init jobs are queued and running
        ACTIVE,           // ready to update and render
        DISPOSING,        // removal jobs running (VAO cleanup etc.)
        DISPOSED          // fully removed, can be GC'd
    }

    volatile State state = State.UNINITIALIZED;
    int renderLayer;      // controls draw order (see render layer table)

    /**
     * Called by World.add(). Return the jobs needed to initialize this object.
     * Implementations set state = LOADING before returning.
     * The last job in the chain sets state = ACTIVE when complete.
     */
    abstract List<Job> onAdd(JobModule jobs);

    /**
     * Called by World.remove(). Return any cleanup jobs (VAO/VBO disposal).
     * Most objects can return List.of() — ModelBuilder tracks all VAOs for shutdown.
     */
    List<Job> onRemove(JobModule jobs) { return List.of(); }

    /**
     * Called once per update frame when state == ACTIVE.
     * Override for per-frame logic. Default is no-op.
     */
    void update(float dt) {}

    /**
     * Called once per render frame when state == ACTIVE.
     * Submit draw calls to the renderer here.
     */
    abstract void render(Renderer renderer, Camera camera);
}
```

`World.add()` is the only entry point:

```java
void add(GameObject obj) {
    objects.add(obj);
    obj.onAdd(jobModule).forEach(j -> jobModule.updateQueue.assign(j));
}
```

---

## The Five Categories

Objects fall into five categories based on where their geometry comes from, how they're rendered, and how they update.

---

### Category 1 — Terrain

**What it is:** One tile of the world surface. Procedurally generated from noise. Exists in multiple LOD levels.

**`TerrainChunk`**

```java
class TerrainChunk extends GameObject {
    final int cx, cz;           // chunk coordinates
    final World world;
    CancelToken cancelToken = new CancelToken();

    float[][] heightmap;        // generated on worker, kept for asset placement
    MeshData[] meshData;        // CPU-side arrays, one per LOD (freed after upload)
    Model[] models;             // GPU VAOs, one per LOD

    int activeLod = -1;         // -1 = not yet selected

    List<Job> onAdd(JobModule jobs) {
        state = State.LOADING;

        Job generate = new Job(Priority.NORMAL) {{
            cancelToken = TerrainChunk.this.cancelToken;
        }
            void code() {
                heightmap = world.heightmapGen.generate(cx, cz);
                if (cancelToken.isCancelled()) return;
                meshData = new MeshData[5];
                for (int lod = 0; lod < 5; lod++) {
                    meshData[lod] = ChunkMesh.build(heightmap, lod);
                    if (cancelToken.isCancelled()) return;
                }
                // asset placement also happens here while heightmap is hot
                world.assetPlacer.place(this);
            }
        };

        Job upload = new Job(Priority.NORMAL) {{
            glThread = true;
            dependsOn.add(generate);
            cancelToken = TerrainChunk.this.cancelToken;
        }
            void code() {
                if (cancelToken.isCancelled()) return;
                models = new Model[5];
                for (int lod = 0; lod < 5; lod++) {
                    models[lod] = ModelBuilder.buildModel(meshData[lod]);
                }
                meshData = null;  // free ~15MB of CPU arrays per chunk
                state = State.ACTIVE;
            }
        };

        return List.of(generate, upload);
    }

    List<Job> onRemove(JobModule jobs) {
        cancelToken.cancel();
        // ModelBuilder already tracks VAOs — they'll be freed at engine shutdown
        // For immediate freeing: add a glThread job that calls model.dispose()
        return List.of();
    }

    void render(Renderer renderer, Camera camera) {
        activeLod = world.lodSelector.select(cx, cz, camera);
        if (activeLod < 0 || models[activeLod] == null) return;
        renderer.renderTerrain(models[activeLod], transform());
    }

    Transform transform() {
        // chunk sits at its world-space corner, no rotation, scale = 1
        return new Transform(new Vec3f(cx * CHUNK_SIZE, 0, cz * CHUNK_SIZE));
    }
}
```

**Job flow:** `GenerateJob` (worker thread, cancellable) → `UploadJob` (GL thread, depends on generate).

**LOD selection** is a pure read per render frame — no jobs, just a distance lookup that returns which model index to bind.

---

### Category 2 — Static Prop

**What it is:** A unique object with a fixed position. Loaded from OBJ. The bird landmark, a specific ancient cliff, a handplaced rock.

**`Prop`**

```java
class Prop extends GameObject {
    final String modelPath;
    final Transform transform;
    Model model;    // null until ACTIVE

    List<Job> onAdd(JobModule jobs) {
        // Hit cache — no jobs needed
        if (ModelCache.has(modelPath)) {
            model = ModelCache.get(modelPath);
            state = State.ACTIVE;
            return List.of();
        }

        state = State.LOADING;
        MeshData[] raw = new MeshData[1];

        Job load = new Job(Priority.NORMAL) {
            void code() {
                // AssimpLoader replaces OBJLoader — handles OBJ, FBX, GLTF, etc.
                raw[0] = AssimpLoader.loadStatic(modelPath);
            }
        };

        Job upload = new Job(Priority.NORMAL) {{
            glThread = true;
            dependsOn.add(load);
        }
            void code() {
                model = ModelBuilder.buildModel(raw[0]);
                ModelCache.set(modelPath, model);
                state = State.ACTIVE;
            }
        };

        return List.of(load, upload);
    }

    void render(Renderer renderer, Camera camera) {
        renderer.renderEntity(model, transform);
    }
}
```

**Model cache:** `ModelCache` is a thread-safe map from path → `Model`. Multiple `Prop` instances sharing the same OBJ path get the same VAO. The second `Prop` to request "oak_tree.obj" finds it already there and skips both jobs.

For the case where two Props request the same path before either has uploaded, the cache also stores in-progress futures so the second load job isn't created at all.

---

### Category 3 — Instanced Asset

**What it is:** Thousands of copies of one mesh. One draw call. Trees, rocks, grass.

Unlike a `Prop`, instances aren't individual objects — they are collectively owned by an `InstanceBatch`. Chunks contribute transforms to the batch; when a chunk unloads, its transforms are removed.

**`InstanceBatch`**

```java
class InstanceBatch extends GameObject {
    final String modelPath;
    Model mesh;

    // Instances grouped by chunk so we can remove them efficiently
    final Map<ChunkKey, float[]> chunkTransforms = new ConcurrentHashMap<>();
    // Flat GPU buffer (rebuilt when dirty)
    volatile float[] gpuData;
    int instanceVBO = -1;
    int instanceCount = 0;
    volatile boolean dirty = false;

    // Called by chunks during asset placement
    void addChunk(ChunkKey key, List<Mat4f> transforms) {
        chunkTransforms.put(key, packToFloats(transforms));
        dirty = true;
    }

    void removeChunk(ChunkKey key) {
        chunkTransforms.remove(key);
        dirty = true;
    }

    List<Job> onAdd(JobModule jobs) {
        // Load the shared mesh (or hit cache)
        // Same load → upload job chain as Prop
        // Then state = ACTIVE — batch renders even with 0 instances
        ...
    }

    void update(float dt) {
        if (dirty) {
            // Rebuild the flat float[] from all chunk contributions
            // This runs on the update thread — packing is CPU work
            gpuData = rebuildGpuData();
        }
    }

    void render(Renderer renderer, Camera camera) {
        if (dirty && gpuData != null) {
            // Upload to instanceVBO — must happen on GL thread (render() IS on GL thread)
            uploadInstanceVBO(gpuData);
            instanceCount = gpuData.length / 16; // 16 floats per mat4
            dirty = false;
        }
        if (instanceCount > 0) {
            renderer.renderInstanced(mesh, instanceVBO, instanceCount);
        }
    }
}
```

**The separation:** CPU rebuild of the float array happens in `update()` (worker/update thread). GPU upload happens in `render()` which runs on the GL thread. This avoids any GL calls off the render thread.

**World manages batches:**

```java
class World {
    Map<String, InstanceBatch> batches = new HashMap<>();

    InstanceBatch getBatch(String assetPath) {
        return batches.computeIfAbsent(assetPath, path -> {
            InstanceBatch b = new InstanceBatch(path);
            add(b);
            return b;
        });
    }
}
```

`AssetPlacer` calls `world.getBatch("oak_tree.obj").addChunk(key, transforms)`. That's the entire API.

---

### Category 4 — Environmental

**What it is:** Global, unique geometry that always exists — sky, water, atmosphere. Not part of the chunk system.

**`Sky`**

```java
class Sky extends GameObject {{
    renderLayer = 0;   // draws first, no depth write
}
    Model skydome;
    float timeOfDay;

    List<Job> onAdd(JobModule jobs) {
        state = State.LOADING;
        MeshData[] mesh = new MeshData[1];

        Job build = new Job(Priority.HIGH) {
            void code() { mesh[0] = MeshBuilder.skydome(500f, 32); }
        };

        Job upload = new Job(Priority.HIGH) {{
            glThread = true; dependsOn.add(build);
        }
            void code() {
                skydome = ModelBuilder.buildModel(mesh[0]);
                state = State.ACTIVE;
            }
        };

        return List.of(build, upload);
    }

    void update(float dt) {
        timeOfDay += dt * TIME_SCALE;
    }

    void render(Renderer renderer, Camera camera) {
        renderer.renderSky(skydome, timeOfDay);
    }
}
```

Environmental objects are created once in `BirdScene.start()` before the world begins streaming. Their jobs complete before the first frame renders.

---

### Category 5 — Dynamic Entity

**What it is:** An object that moves and updates every frame. The player bird. Eventually, flocking birds and other creatures.

**`Bird` (player)**

```java
class Bird extends DynamicEntity {
    FlightPhysics physics;
    FlightCamera camera;
    Model mesh;

    List<Job> onAdd(JobModule jobs) {
        // Mesh loading is same as Prop (load → upload job chain)
        // Additionally registers a CRITICAL priority update job that runs every frame
        state = State.LOADING;
        ... load/upload mesh jobs ...

        // The physics update is not a one-shot job — it re-queues itself
        Job physicsJob = new RepeatingJob(Priority.CRITICAL) {
            void code() {
                if (state != State.ACTIVE) return;
                physics.update(deltaTime);
                camera.update(deltaTime);
            }
        };
        jobs.updateQueue.assign(physicsJob);

        return List.of(loadJob, uploadJob);
        // physicsJob is assigned directly, not returned — it manages itself
    }

    void render(Renderer renderer, Camera camera) {
        renderer.renderEntity(mesh, physics.transform());
    }
}
```

A `RepeatingJob` is a small extension to `Job` — when `postCode()` is called it re-adds itself to the queue for the next frame. This keeps the physics loop running without the scene having to manage it.

---

## `ModelCache`

Prevents loading the same OBJ file twice. Thread-safe.

```java
class ModelCache {
    enum EntryState { LOADING, READY }

    record Entry(EntryState state, Model model) {}

    static final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    static boolean has(String path) {
        Entry e = cache.get(path);
        return e != null && e.state() == READY;
    }

    static Model get(String path) { return cache.get(path).model(); }

    // Called by load job before file read begins
    static void reserve(String path) {
        cache.putIfAbsent(path, new Entry(LOADING, null));
    }

    // Called by upload job after VAO is created
    static void set(String path, Model model) {
        cache.put(path, new Entry(READY, model));
    }

    // True if another job is already loading this path
    static boolean isPending(String path) {
        Entry e = cache.get(path);
        return e != null && e.state() == LOADING;
    }
}
```

If two `Prop`s for the same path are added before either finishes loading, the second one's `onAdd()` sees `isPending()` and registers a callback rather than creating duplicate jobs. (The simplest implementation: poll in `update()` until cache is READY, then assign `model`.)

---

## Transform

Owns position, rotation, scale for one object. Immutable during a frame; written by update thread, read by render thread.

```java
class Transform {
    Vec3f position;
    Quaternion rotation;
    Vec3f scale = Vec3f.ONE;

    Mat4f toMatrix() { /* scale → rotate → translate */ }
    Transform copy()  { /* snapshot for render thread */ }
}
```

For dynamic entities that update every frame, the update thread writes `transform`, and the render thread calls `transform.copy()` to get a consistent snapshot. Terrain and props are static — no copy needed.

---

## Render Layer Table

| Layer | Objects | GL state |
|-------|---------|----------|
| 0 | Sky | No depth write, no depth test |
| 1 | Terrain chunks | Opaque, depth write |
| 2 | Props (unique static) | Opaque, depth write |
| 3 | Instanced assets (trees, rocks) | Opaque, depth write |
| 4 | Billboard fallbacks | Alpha test, no blend |
| 5 | Dynamic entities (bird) | Opaque, depth write |
| 6 | Water | Transparent, blend |
| 7 | Particles | Additive blend |
| 8 | UI / flamegraph | No depth test |

---

## `World` — The Manager

```java
class World {
    List<GameObject> objects = new ArrayList<>();
    Map<ChunkKey, TerrainChunk> chunks = new HashMap<>();
    Map<String, InstanceBatch> batches = new HashMap<>();
    HeightmapGenerator heightmapGen;
    AssetPlacer assetPlacer;
    LODSelector lodSelector;

    void add(GameObject obj) {
        objects.add(obj);
        obj.onAdd(jobModule).forEach(j -> jobModule.updateQueue.assign(j));
    }

    void remove(GameObject obj) {
        objects.remove(obj);
        obj.onRemove(jobModule).forEach(j -> jobModule.updateQueue.assign(j));
    }

    // Called each update frame — manages chunk streaming
    void update(Vec3f cameraPos, Vec3f cameraVelocity) {
        updateChunks(cameraPos, cameraVelocity);
        for (GameObject obj : objects) {
            if (obj.state == ACTIVE) obj.update(deltaTime);
        }
    }

    // Called each render frame
    void render(Renderer renderer, Camera camera) {
        // Sort by render layer, draw in order
        for (int layer = 0; layer <= MAX_LAYER; layer++) {
            for (GameObject obj : objectsInLayer(layer)) {
                if (obj.state == ACTIVE) obj.render(renderer, camera);
            }
        }
    }
}
```

---

## Summary: Who Does What

| Concern | Handled by |
|---------|-----------|
| Geometry source (authored asset) | `AssimpLoader` inside `Prop.onAdd()` / `AnimatedEntity.onAdd()` job |
| Geometry source (procedural) | `ChunkMesh` / `MeshBuilder` inside `TerrainChunk.onAdd()` job |
| GPU upload | `ModelBuilder` inside GL-pinned job |
| Asset caching | `ModelCache` — transparent to callers |
| Instancing | `InstanceBatch` — chunks call `addChunk()`, batch manages VBO |
| LOD selection | `LODSelector` — pure distance calculation, no jobs |
| Transform / matrix | `Transform.toMatrix()` — called by each object in `render()` |
| Job scheduling | `World.add()` queues returned jobs — nothing else touches `JobModule` |
| Thread safety | LOADING→ACTIVE transition is the fence; render ignores non-ACTIVE objects |

---

## Open Questions

- [ ] `RepeatingJob` design: re-queue in `postCode()`, or does `JobModule` have a concept of standing/recurring jobs?
- [ ] When two Props share a model and one is removed, who decides when the VAO is freed? Reference counting in `ModelCache`?
- [ ] `Transform` copy for dynamic entities: double-buffer (write one, read other) or volatile + snapshot in render?
- [ ] `InstanceBatch` dirty rebuild: if 50 chunks load in one frame, should the rebuild job be deferred to the next frame or done immediately?
- [ ] Should `World.update()` and `World.render()` themselves be `Job`s so they appear in the flamegraph?
