# v4 Model System

## Problem with v3

`testscene.java` manages a single model by holding a `Model` reference and manually building its matrix every frame. Scaling this to hundreds of objects means duplicating that matrix logic everywhere. There is no concept of "an object in the world with a position."

---

## Core Additions

### `Transform`

A `Transform` owns the spatial state of one object: position, rotation, scale. It computes a model matrix on demand.

```java
class Transform {
    Vec3f position;
    Quaternion rotation;
    Vec3f scale;           // (1, 1, 1) by default

    Mat4f toMatrix();      // compose scale → rotate → translate
    void translate(Vec3f delta);
    void rotate(Quaternion delta);
}
```

This replaces the matrix assembly that currently lives scattered across scene `update()` jobs. The matrix composition logic already exists in `JobModule.matrixWork` — `Transform.toMatrix()` is essentially that, packaged per-object.

---

### `Entity`

An `Entity` pairs a `Model` (or `TexturedModel`) with a `Transform`. It is the basic "thing in the world."

```java
class Entity {
    Model model;
    Transform transform;
    // optional: String name, boolean visible, int renderLayer
}
```

Scenes build lists of `Entity` objects. The renderer iterates them, binding each one's matrix before the draw call. No other change to rendering logic needed — `Renderer` already handles `MatrixBinding` per draw call.

---

### `MeshBuilder`

A utility class for generating geometry programmatically. Produces the same `float[]` arrays that `ModelBuilder` already expects, so no changes to the GPU upload path.

Planned generators:

| Method | Output | Used for |
|--------|--------|----------|
| `plane(int w, int h, float scale)` | Flat grid mesh | Flat terrain testing |
| `cube(float size)` | Unit cube | Voxel blocks, bounding box debug |
| `sphere(int rings, int slices, float r)` | UV sphere | Props, debug shapes |
| `terrainChunk(float[][] heightmap, float scale)` | Grid mesh with Y from heightmap | Terrain rendering |
| `voxelChunk(byte[][][] blocks, ...)` | Face-culled block mesh | Voxel world |

All methods return a `MeshData` record:

```java
record MeshData(float[] vertices, int[] indices, float[] normals, float[] colors) {}
```

This feeds directly into `ModelBuilder.buildModel(vertices, indices, colors, normals)`.

Normals for terrain and voxels must be computed during mesh generation (cross product of triangle edges), not imported from an OBJ.

---

## Instanced Rendering

For objects that repeat many times (trees, grass tufts, rocks) instancing avoids one draw call per object.

### How it works

Instead of binding a different model matrix uniform for each draw call, OpenGL reads per-instance data from a second VBO bound to the same VAO. A single `glDrawElementsInstanced(count, instanceCount)` call renders all copies.

### `InstancedModel`

```java
class InstancedModel {
    Model model;
    int instanceVBO;       // VBO holding per-instance mat4 data
    int instanceCount;

    void updateInstances(Mat4f[] transforms); // uploads to instanceVBO
    void render();                            // glDrawElementsInstanced
}
```

The instance VBO uses 4 consecutive `vec4` attributes (locations 4–7) to pass a full `mat4` per instance. The vertex shader reads them and uses them in place of the `m` uniform.

This requires a second shader variant or a `#define` toggle in the existing shader.

### When to use it

| Use case | Approach |
|----------|----------|
| Unique objects (player, landmarks) | `Entity` with individual draw call |
| Hundreds of trees | `InstancedModel` |
| Terrain chunks | Individual `Entity` per chunk (each chunk is already one mesh) |
| Grass | `InstancedModel`, generated per visible chunk |

---

## How Scenes Use This

A v4 scene holds:

```java
ArrayList<Entity> entities;         // unique/authored objects
ArrayList<InstancedModel> instanced; // repeated objects
World world;                        // chunk-based terrain (see world-generation.md)
```

The `render()` job list:
1. Render terrain chunks from `world`
2. Render each entity (matrix per draw call)
3. Render each `InstancedModel` (one call per type)
4. Render flamegraph overlay (unchanged)

The `update()` job list remains structurally unchanged — it can add entity transform update jobs alongside the existing `controls` and `matrixWork` jobs.

---

## Open Questions

- [ ] Should `Transform` support parent-child hierarchy (scene graph), or stay flat?
- [ ] Does `Entity` need a per-object update callback, or is the scene responsible for all logic?
- [ ] Frustum culling: skip entities outside the view frustum. Add to `Entity` or `Renderer`?
- [ ] Should `InstancedModel` instance data be updated every frame or only on change?
