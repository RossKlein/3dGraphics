# v4 Skeletal Animation

## Why Assimp Replaces OBJLoader

The existing `OBJLoader` is a hand-written OBJ parser. OBJ does not support skeletal animation, bone weights, or armatures at all. Assimp (Open Asset Import Library) is included in LWJGL 3 and handles:

- 40+ formats with one unified API
- Vertex bone indices and weights (up to 4 per vertex)
- The full bone hierarchy as a node tree
- Animation clips with per-bone keyframe channels
- Material and texture information
- Post-processing: normal generation, tangent generation, mesh optimization, bone weight limiting

**`OBJLoader` is replaced entirely by `AssimpLoader`.** For non-animated models, you ignore the animation data. For animated models, you read it. Same call either way.

MeshBuilder is unaffected — Assimp loads authored assets, MeshBuilder generates procedural geometry.

---

## File Format Recommendation

Blender supports exporting to FBX, COLLADA (.dae), and GLTF 2.0. All three carry skeletal animation data. However:

| Format | Assimp Support | Notes |
|--------|---------------|-------|
| **FBX** | Mature, reliable | Recommended for animated models. Proprietary but ubiquitous. |
| **COLLADA (.dae)** | Mature, reliable | Open standard. Good alternative to FBX. |
| **GLTF 2.0** | Known issues | Assimp has bugs with multiple joint weight sets in older versions. Modern Assimp (5.2.5+) is better, but FBX is still safer. |

**Recommendation:** Export the bird from Blender as FBX. Use GLTF for static props if you want (it's fine for meshes without animation).

---

## What Assimp Provides

After `aiImportFile(path, flags)`:

```
AIScene
├── mRootNode (AINode)         ← the bone hierarchy root
│   ├── name, transform
│   └── mChildren[]            ← each child is a bone or transform node
├── mMeshes[] (AIMesh)
│   ├── mVertices[]            ← positions
│   ├── mNormals[]
│   ├── mTextureCoords[][]
│   ├── mFaces[]               ← triangle indices
│   └── mBones[] (AIBone)
│       ├── mName              ← matches a node name in the hierarchy
│       ├── mOffsetMatrix      ← inverse bind pose matrix
│       └── mWeights[]         ← (vertexIndex, weight) pairs
└── mAnimations[] (AIAnimation)
    ├── mName                  ← e.g. "flap", "glide"
    ├── mDuration              ← in ticks
    ├── mTicksPerSecond
    └── mChannels[] (AINodeAnim)
        ├── mNodeName          ← which bone this channel drives
        ├── mPositionKeys[]    ← (time, Vec3) keyframes
        ├── mRotationKeys[]    ← (time, Quaternion) keyframes
        └── mScalingKeys[]     ← (time, Vec3) keyframes
```

Recommended post-processing flags:

```java
int flags = aiProcess_Triangulate
          | aiProcess_GenSmoothNormals
          | aiProcess_CalcTangentSpace     // needed for normal mapping
          | aiProcess_LimitBoneWeights     // cap at 4 weights per vertex
          | aiProcess_JoinIdenticalVertices;
// DO NOT use aiProcess_PreTransformVertices on animated models
// — it bakes the bind pose into vertex positions and destroys animation data
```

---

## Runtime Data Structures

Assimp is used only at load time. The runtime animation system works entirely with these engine types:

### `Skeleton`

The bone hierarchy extracted from the node tree. Stored once per model, shared across all instances.

```java
class Skeleton {
    String[] boneNames;          // index → name, matches AIBone.mName
    int[] parentIndex;           // index → parent bone index (-1 for root)
    Mat4f[] offsetMatrices;      // index → inverse bind pose (from AIBone.mOffsetMatrix)
    Mat4f[] localBindPose;       // index → bind pose local transform (from AINode.mTransformation)
    int boneCount;
}
```

### `AnimationClip`

One named animation from `AIAnimation`.

```java
class AnimationClip {
    String name;                   // "flap", "glide", "bank_left", etc.
    float duration;                // total length in seconds
    boolean loops;

    // Per-bone channel — index matches skeleton.boneNames
    Vec3f[][] positionKeys;        // [boneIndex][keyframeIndex]
    float[]   positionTimes;       // shared time axis
    Quaternion[][] rotationKeys;
    float[]        rotationTimes;
    Vec3f[][] scaleKeys;
    float[]   scaleTimes;
}
```

### `AnimationController`

One per animated entity instance. Manages current state, time, and blending.

```java
class AnimationController {
    Skeleton skeleton;
    AnimationClip[] clips;         // all clips loaded with this model

    AnimationClip currentClip;
    AnimationClip targetClip;      // for cross-fade blending
    float time;                    // current playback time (seconds)
    float blendTime;               // 0.0 → 1.0 cross-fade progress
    float blendDuration;           // how long the cross-fade takes

    Mat4f[] boneMatrices;          // [boneCount] — uploaded to shader each frame

    void play(String clipName);
    void crossFadeTo(String clipName, float duration);
    void update(float dt);         // advances time, recomputes boneMatrices
    void uploadToShader(Shader shader); // glUniformMatrix4fv boneMatrices array
}
```

---

## Animation Update — Per Frame

`AnimationController.update(dt)` runs as a CRITICAL priority job (same as flight physics):

```
1. Advance time by dt (loop or clamp at clip end)
2. For each bone in skeleton:
   a. Find surrounding keyframes in currentClip for current time
   b. Lerp position, slerp rotation, lerp scale between keyframes
   c. Build local transform matrix from interpolated TRS
   d. If blending: do the same for targetClip, lerp results by blendTime
3. Traverse bone hierarchy (BFS or DFS, parent before child):
   a. globalTransform[bone] = globalTransform[parent] * localTransform[bone]
4. Final bone matrix:
   boneMatrices[i] = globalTransform[i] * skeleton.offsetMatrices[i]
   (offsetMatrix transforms from mesh space → bone space,
    globalTransform transforms from bone space → world space)
5. Upload boneMatrices[] to shader uniform
```

This is pure matrix math — fits naturally as a Job with no dependencies on GL.

---

## GPU Skinning — Shader

A skinned shader variant reads bone indices and weights as extra vertex attributes.

**Vertex attributes for skinned meshes:**

| Location | Name | Type | Contents |
|----------|------|------|---------|
| 0 | position | vec3 | vertex position |
| 1 | color | vec4 | vertex color |
| 2 | normal | vec3 | vertex normal |
| 3 | uv | vec2 | texture coords |
| 4 | boneIndices | ivec4 | 4 bone indices into boneMatrices[] |
| 5 | boneWeights | vec4 | 4 corresponding weights (sum = 1.0) |

**Vertex shader (skinned):**

```glsl
#version 400 core

layout(location = 0) in vec3 position;
layout(location = 2) in vec3 normal;
layout(location = 4) in ivec4 boneIndices;
layout(location = 5) in vec4 boneWeights;

uniform mat4 m, v, p;
uniform mat4 boneMatrices[128];   // max bones per model

void main() {
    // Build weighted skin matrix from up to 4 bone contributions
    mat4 skinMatrix =
        boneMatrices[boneIndices.x] * boneWeights.x +
        boneMatrices[boneIndices.y] * boneWeights.y +
        boneMatrices[boneIndices.z] * boneWeights.z +
        boneMatrices[boneIndices.w] * boneWeights.w;

    vec4 skinnedPos    = skinMatrix * vec4(position, 1.0);
    vec3 skinnedNormal = normalize(mat3(skinMatrix) * normal);

    gl_Position = p * v * m * skinnedPos;
    // pass skinnedNormal to fragment stage for lighting
}
```

Static meshes use the existing shader (no boneIndices/boneWeights attributes). Two shader variants, or one shader with a `uniform bool skinned` flag.

---

## `AnimatedEntity` — Category 6

Extends the entity system with a sixth category for skinned, animated objects.

```java
class AnimatedEntity extends GameObject {
    final String modelPath;
    Transform transform;
    Skeleton skeleton;               // shared — loaded once per model path
    AnimationClip[] clips;           // shared — loaded once per model path
    AnimationController controller;  // per-instance — each bird has its own playback state

    List<Job> onAdd(JobModule jobs) {
        state = State.LOADING;
        AssimpData[] raw = new AssimpData[1];

        Job load = new Job(Priority.HIGH) {
            void code() { raw[0] = AssimpLoader.load(modelPath); }
        };

        Job upload = new Job(Priority.HIGH) {{
            glThread = true;
            dependsOn.add(load);
        }
            void code() {
                model    = ModelBuilder.buildSkinnedModel(raw[0]);
                skeleton = SkeletonBuilder.build(raw[0]);
                clips    = AnimationClipBuilder.buildAll(raw[0]);
                controller = new AnimationController(skeleton, clips);
                controller.play("glide");
                state = State.ACTIVE;
            }
        };

        return List.of(load, upload);
    }

    void update(float dt) {
        controller.update(dt);
    }

    void render(Renderer renderer, Camera camera) {
        controller.uploadToShader(renderer.skinnedShader);
        renderer.renderSkinnedEntity(model, transform);
    }
}
```

---

## Bird Animation Design

The player bird needs these clips, exported from Blender:

| Clip Name | Trigger | Blends to |
|-----------|---------|-----------|
| `glide` | Default, high speed | `flap` when speed drops |
| `flap` | Low speed, climbing | `glide` when speed rises |
| `bank_left` | Turning left | `glide`/`flap` |
| `bank_right` | Turning right | `glide`/`flap` |
| `dive` | Steep descent, high speed | `glide` |
| `land` | Velocity near zero | One-shot, then idle |
| `idle` | Perched | `flap` on takeoff |

`FlightPhysics.update()` drives the controller:

```java
if (speed > GLIDE_THRESHOLD)    controller.crossFadeTo("glide", 0.3f);
else                            controller.crossFadeTo("flap",  0.2f);
if (Math.abs(bankAngle) > 0.3f) controller.layerBlend("bank_left" or "bank_right", bankAngle);
```

Cross-fades are short (0.2–0.3s). The bird should never snap between poses.

---

## `AssimpLoader` — Replaces `OBJLoader`

```java
class AssimpLoader {

    static final int DEFAULT_FLAGS =
        aiProcess_Triangulate |
        aiProcess_GenSmoothNormals |
        aiProcess_CalcTangentSpace |
        aiProcess_LimitBoneWeights |
        aiProcess_JoinIdenticalVertices;

    // For static meshes — returns same MeshData as before
    static MeshData loadStatic(String path);

    // For animated meshes — returns mesh data + skeleton + animation clips
    static AssimpData loadAnimated(String path);
}

record AssimpData(
    MeshDataSkinned mesh,      // vertices, indices, normals, uvs, boneIndices, boneWeights
    SkeletonData skeleton,     // bone names, parent indices, offset matrices
    List<ClipData> clips       // raw keyframe data for all animations in file
) {}
```

The `Prop` category uses `loadStatic()`. The `AnimatedEntity` category uses `loadAnimated()`. Both use the same underlying `aiImportFile()` call — just different extraction paths on the result.

---

## Open Questions

- [ ] Max bones per model: 128 is the uniform array size in the shader. Is this enough for the bird? (Wings likely need ~20–30 bones. 128 is generous.)
- [ ] Animation blending layers: full cross-fade works for primary clips, but banking while flapping needs additive blending on a secondary layer. Defer to post-M1?
- [ ] Shared skeleton vs per-instance: skeleton and clips are shared data (loaded once). But `boneMatrices[]` is per-instance. Is there ever a case where two birds share the same controller? No — each needs independent playback state.
- [ ] Inverse kinematics (IK): feet touching a perch point. Significant complexity. Defer to late milestone.
- [ ] How does `ModelCache` handle `AssimpData`? The raw data can be freed after upload. Only `Skeleton` and `AnimationClip[]` need to be kept.
