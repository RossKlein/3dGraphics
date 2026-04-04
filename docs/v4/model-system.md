# v4 Model System

## Problem with v3

`testscene.java` manages a single model by holding a `Model` reference and manually building its matrix every frame. A world with thousands of trees, rocks, and terrain chunks cannot work this way — there is no concept of "an object in the world with a position."

---

## `Transform`

Owns the spatial state of one object: position, rotation (quaternion), scale. Computes a model matrix on demand.

```java
class Transform {
    Vec3f position;
    Quaternion rotation;
    Vec3f scale;           // (1, 1, 1) by default

    Mat4f toMatrix();      // scale → rotate → translate
    void translate(Vec3f delta);
    void rotate(Quaternion delta);
}
```

The matrix composition logic already exists in `JobModule.matrixWork` — `Transform.toMatrix()` extracts and packages it per-object.

---

## `Entity`

An `Entity` pairs a `Model` with a `Transform`. It is a unique object in the world — the bird, a landmark, a specific rock.

```java
class Entity {
    Model model;         // or TexturedModel
    Transform transform;
    boolean visible;
    int renderLayer;     // 0=terrain, 1=opaque, 2=transparent, 3=overlay
}
```

Scenes hold `ArrayList<Entity>`. The renderer iterates them, binds each matrix, and draws. No change to the core rendering logic — `Renderer` already accepts `MatrixBinding` per draw.

---

## `InstancedModel`

For objects that repeat — trees, rocks, grass tufts — instanced rendering sends one draw call with all per-instance transforms packed in a VBO. This is critical for a world with thousands of trees.

```java
class InstancedModel {
    Model model;
    int instanceVBO;          // VBO holding per-instance mat4 data (4x vec4 = attrib 4-7)
    int instanceCount;
    boolean dirty;            // true if instance data needs re-upload

    void setInstances(Mat4f[] transforms);   // rebuilds instanceVBO
    void addInstance(Mat4f transform);       // marks dirty
    void uploadIfDirty();                    // called by renderer before draw
    void render();                           // glDrawElementsInstanced
}
```

The vertex shader reads instance transform from `layout(location = 4..7)` attributes (4 vec4s = 1 mat4). A `#define INSTANCED` toggle in the shader handles both instanced and non-instanced paths.

### Per-Asset-Type `InstancedModel`

Each tree type and rock type has its own `InstancedModel`. When a chunk loads, its asset list (positions, rotations, scales) is appended to the appropriate `InstancedModel` buffer. When a chunk unloads, its instances are removed and the buffer is marked dirty.

---

## `Billboard`

Camera-facing quads for distant assets (trees and rocks beyond 8 chunks). Cheaper to render than even a low-poly mesh.

```java
class Billboard {
    int vao;               // simple quad VAO
    int texture;           // pre-rendered or simple sprite texture
    Vec3f worldPosition;
    float size;
}
```

Billboards are also instanced — one draw call per asset type at billboard distance. The billboard vertex shader orients the quad to always face the camera.

---

## `MeshBuilder`

Utility for generating geometry at runtime. Returns `MeshData` records that feed directly into `ModelBuilder`.

```java
record MeshData(float[] vertices, int[] indices, float[] normals, float[] colors) {}
record MeshDataUV(float[] vertices, int[] indices, float[] normals, float[] colors, float[] uvs) {}
```

Planned generators:

| Method | Used for |
|--------|----------|
| `quad(float w, float h)` | Water plane, billboard, sky plane |
| `cube(float size)` | Debug bounding boxes |
| `terrainChunk(float[][] heightmap, int lod)` | Terrain mesh from heightmap |
| `skydome(float radius, int segments)` | Sky geometry |

The bird model and rocks are loaded via `OBJLoader` — `MeshBuilder` is only for procedural geometry.

---

## Render Layers and Order

Rendering has a fixed order to handle transparency correctly:

| Layer | Contents | Notes |
|-------|----------|-------|
| 0 | Sky (skydome or gradient quad) | No depth write |
| 1 | Terrain chunks | Opaque, depth write |
| 2 | Opaque entities (rocks, bird) | Opaque, depth write |
| 3 | Instanced assets (trees, rocks) | Opaque, depth write |
| 4 | Billboards (distant trees) | Alpha test |
| 5 | Water | Transparent, blended |
| 6 | Particles / atmospheric | Additive blend |
| 7 | UI / flamegraph overlay | No depth test |

The renderer iterates entities and instanced models sorted by layer.

---

## Flight Camera

The camera in v4 is not the free-floating FPS camera from v3. It follows the bird with lag:

```java
class FlightCamera {
    Vec3f targetPosition;     // bird position
    Vec3f currentPosition;    // smoothly follows target
    Quaternion targetRotation;
    Quaternion currentRotation;
    float lagFactor;           // how tightly camera follows (0=instant, 1=never)

    void update(float dt);            // lerp toward target
    Mat4f getViewMatrix();            // same quaternion→matrix as v3
}
```

The existing quaternion view matrix generation is reused unchanged.

---

## Open Questions

- [ ] Does `Entity` need a hierarchy (parent-child transforms) or stay flat? Flat is simpler and the bird doesn't need it yet.
- [ ] How is the instance buffer managed when many chunks load/unload simultaneously? Double-buffer to avoid GPU stalls?
- [ ] Frustum culling: per-entity (easy) and per-chunk (needed). Where does this live — `Renderer` or `World`?
- [ ] The bird model: OBJ is static. Wing animation needs either skeletal animation (complex) or a wind-style vertex shader (simpler — deform wings by speed).
