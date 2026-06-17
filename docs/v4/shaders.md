# v4 Shaders

## Philosophy

Every visual effect that can be done in a shader should be done in a shader. CPU is reserved for simulation and streaming. GPU is cheap for per-pixel math. This is the Subnautica approach — the world looks rich not because of polygon count but because of what happens to each pixel.

---

## Current Shaders (v3)

`staticShader` — handles both colored and textured models with a single point light (Phong). This is the foundation everything else builds from.

---

## Planned Shaders

### `terrainShader`

Terrain-specific. Uses vertex color (no texture) with slope-based and height-based effects.

**Vertex stage:**
- Pass height, slope (from normal), and biome ID to fragment
- Apply small noise-based vertex offset for micro-variation (optional)

**Fragment stage:**
- Base color from vertex color (set during mesh generation)
- Slope darkening: `color *= mix(1.0, 0.6, clamp(slope * 2.0, 0.0, 1.0))`
- Snow blending above `alpineLine`: `mix(baseColor, snowColor, snowBlend)`
- Phong lighting with sun as directional light
- **Atmospheric haze** (see below)

---

### `skyShader`

Draws the sky. Either a skydome mesh or a fullscreen quad rendered before everything else.

**Fragment stage:**
- Gradient from horizon color to zenith color based on `dot(normalize(fragDir), up)`
- Sun disk: bright spot in the direction of `sunDirection` uniform
- Sunrise/sunset color shift: when `sunDirection.y` is near 0, blend warm orange/pink into horizon

No lighting calculations. No depth write.

---

### `waterShader`

The water plane at sea level.

**Vertex stage:**
- Animated wave offset: `y += sin(worldX * freq + time) * amp + sin(worldZ * freq2 + time) * amp`
- Cheap, convincing at altitude

**Fragment stage:**
- Base water color (deep blue-green)
- Shore foam: proximity to terrain edges (sample nearby heightmap or use depth buffer)
- Specular highlight from sun direction (Phong specular, high shininess)
- Fresnel effect: shallow viewing angle = more reflection

---

### `instancedShader`

Variant of `staticShader` with instanced transform support.

Uses `#define INSTANCED` to switch between:
- Normal: read model matrix from `uniform mat4 m`
- Instanced: read from `layout(location = 4..7) in mat4 instanceMatrix`

Otherwise identical to `staticShader`. One shader, two modes.

---

### `billboardShader`

Camera-facing quads for distant trees/rocks.

**Vertex stage:**
- Receive instance world position
- Build a camera-facing transform in the shader: use camera right and up vectors (passed as uniforms) to orient the quad
- Scale by `size` attribute

**Fragment stage:**
- Sample texture (simple top-down silhouette sprite)
- Alpha test: `if (color.a < 0.5) discard` — hard cutoff, no blending needed

---

## Atmospheric Haze

This is the most important effect. Applied in the **fragment stage of every world shader** (terrain, instanced assets, water).

```glsl
uniform vec3 hazeColor;      // matches sky horizon color
uniform float hazeStart;     // distance where haze begins
uniform float hazeDensity;   // exponential falloff rate

// in fragment shader:
float dist = length(fragWorldPos - cameraPos);
float hazeFactor = 1.0 - exp(-max(0.0, dist - hazeStart) * hazeDensity);
fragColor = mix(fragColor, hazeColor, hazeFactor);
```

`hazeColor` is updated every frame to match the current sky horizon color (driven by time of day). This makes the haze feel like part of the sky, not a grey fog.

**Tuning**: `hazeStart` and `hazeDensity` are set so that LOD transitions happen inside the haze zone. The exact values depend on chunk size and LOD distances.

---

## God Rays (Sun Shafts)

Post-processing effect — rendered after the main scene into a framebuffer, then composited.

Simple radial blur technique (not volumetric, but convincing):
1. Render scene to texture
2. Project sun position to screen space
3. Radially blur outward from sun position: sample along rays toward sun, accumulate bright pixels
4. Blend additively onto final image

This is cheap (~1ms) and looks spectacular at sunrise/sunset.

---

## Normal Mapping on Terrain

Even with vertex normals, terrain can look flat up close. A terrain normal map (tiling detail texture) adds micro surface variation:

```glsl
vec3 detailNormal = texture(normalMap, uv * detailScale).rgb * 2.0 - 1.0;
vec3 finalNormal = normalize(vertexNormal + detailNormal * detailStrength);
```

Used only in LOD 0 and 1 (close range). Detail normal fades out at distance.

---

## Wind Vertex Shader

Applied to tree/foliage meshes. No CPU cost.

```glsl
uniform float time;
uniform float windStrength;
uniform vec3 windDirection;

// in vertex shader:
float wave = sin(time * 2.0 + worldPos.x * 0.5 + worldPos.z * 0.3);
float heightFactor = max(0.0, position.y / treeHeight); // tip sways more than base
vec3 offset = windDirection * wave * windStrength * heightFactor;
gl_Position = p * v * m * vec4(position + offset, 1.0);
```

Cheap, convincing, and completely free at runtime.

---

## Time of Day

One `float time` uniform drives everything:

| Parameter | Formula |
|-----------|---------|
| Sun direction | `vec3(cos(time), sin(time), 0.3)` (simplified) |
| Sky zenith color | Blue at noon, deep blue at night |
| Sky horizon color | Orange/pink at sunrise/set, pale blue at noon |
| Haze color | Matches horizon |
| Light color | Warm white at noon, orange at sunset, cold blue at night |
| Light intensity | `max(0.1, sin(time))` — ambient stays above 0.1 |

All driven by a single `time` float updated once per frame. No per-object cost.

---

## Shader Roadmap

| Shader | M1 | M2 | M3 |
|--------|----|----|-----|
| Terrain (vertex color + haze) | ✓ | | |
| Sky gradient | ✓ | | |
| Atmospheric haze | ✓ | | |
| Normal mapping | | ✓ | |
| Instanced assets | | ✓ | |
| Water basic | | ✓ | |
| Time of day | | ✓ | |
| God rays | | ✓ | |
| Wind vertex shader | | | ✓ |
| Billboards | | | ✓ |
| Water shore foam / fresnel | | | ✓ |

---

## Open Questions

- [ ] Post-processing framebuffer: need to add FBO support to the engine for god rays and any future effects
- [ ] GLSL version: currently 4.0. Compute shaders (4.3) could be useful for noise generation. Worth upgrading?
- [ ] Tone mapping / gamma correction: currently none in v3. Important for HDR-style sun and bloom.
- [ ] Shadow maps: directional shadow from sun. Significant complexity. Defer to after M2.
