# Game Vision — Bird Flight

## The Experience

You are a bird. The world is vast, procedurally generated, and beautiful. You fly. The goal is to capture the feeling of Subnautica but in the sky — a sense of scale, atmosphere, and discovery. The world rewards exploration: different biomes, weather, time of day, things to find.

This is not a survival game at this stage. The first milestone is a world that feels alive and looks stunning to fly through.

---

## Reference: What Subnautica Does Right

Subnautica (Unity, ~2018) looks far better than its technical budget should allow. The tricks:

| Trick | What it does | Our equivalent |
|-------|-------------|----------------|
| **Exponential fog** | Hides low-poly distant terrain, creates depth | Atmospheric haze, horizon fade |
| **God rays** | Fake volumetric light through water | Sun shafts through clouds |
| **Depth-based color grading** | World gets bluer/darker with depth | Altitude-based color shift |
| **Vertex color terrain** | No tiling texture seams, smooth blending | Slope/height vertex color on terrain |
| **Normal maps everywhere** | High-detail look from low-poly geo | Normal mapped terrain and rocks |
| **LOD + fog** | Low-poly distant meshes hidden by fog | Same — haze is our LOD cover |
| **Bioluminescent emissives** | Lights in dark areas, cheap to render | Sunrise/sunset emissive glow on peaks |
| **Particle atmosphere** | Bubbles, particles make world feel alive | Dust motes, birds, leaf particles |

The key insight: **atmosphere is not just art — it's a performance budget**. You don't need to render what you can't see through haze. We design the visual style to give us maximum LOD flexibility.

---

## The World

### Biomes (planned)

Generated from two noise maps (height + humidity):

| Biome | Height | Humidity | Feel |
|-------|--------|----------|------|
| Ocean/Coast | Low | High | Cliffs, sea spray, dramatic |
| Plains | Low | Medium | Rolling grass, thermals |
| Forest | Low-Med | High | Dense canopy, shafts of light |
| Hills | Medium | Medium | Open, wind, good soaring |
| Mountains | High | Low | Snow, exposed rock, eagles |
| Alpine | Very High | Very Low | Thin atmosphere, ice, mist |

### Scale

- One "chunk" = 64 world units square
- View distance: ~20 chunks (1280 world units) — fades into haze beyond
- World: effectively infinite (stream and unload chunks)
- Vertical range: ~500 world units (sea level to max flight ceiling)

### What Exists in the World

- **Terrain** — heightmap-based mesh, the ground
- **Water** — planes at sea level with a reflective/animated shader
- **Trees / Forest** — instanced meshes close-up, billboards at distance
- **Rocks and cliffs** — instanced OBJ props placed by procedural rules
- **Clouds** — billboard planes in a cloud layer, or layered simple meshes
- **The bird** — player character, authored OBJ with wing animation
- **Other birds** — flocks, procedurally placed and flocked (later)

---

## Flight Feel

The camera follows the bird in third person (slightly above and behind). The bird banks on turns, pitches up/down with altitude change. Speed varies — dive to accelerate, pull up to slow and gain height.

Atmosphere responds to altitude:
- Low: dense haze, greenery, warmer colors
- High: thinner haze, colder, more blue/purple sky
- Very high: almost no haze, stars visible at night

Thermals (rising air columns above sun-warmed terrain) could eventually let you soar without flapping — a natural reward for finding the right places.

---

## Visual Targets

- Dynamic time of day (sun position drives all lighting)
- Atmospheric scattering — sky color shifts from orange at horizon to deep blue at zenith
- God rays / sun shafts through cloud gaps
- Wind animation on foliage (vertex shader, no CPU cost)
- Water reflection and shore foam
- Cloud shadows moving across terrain

These are all shader effects — no additional geometry or CPU cost. This is the Subnautica approach.

---

## Performance Philosophy

1. **Atmosphere hides LOD transitions** — design haze distance to match LOD pop distance
2. **Instancing for everything repeated** — one draw call for all trees of a type
3. **Billboards for distant detail** — trees become textured quads at distance
4. **Chunk generation on workers** — never block the render thread
5. **Predictive chunk loading** — load chunks ahead of flight direction, not just around current position
6. **Frustum cull aggressively** — flying means ~50% of chunks behind you at all times
7. **Job priority by distance** — nearest chunks generate first, distant ones can wait

---

## Milestones

### M1 — Flyable World
- [ ] Terrain generation (heightmap, biome coloring)
- [ ] Chunk streaming with LOD
- [ ] Basic flight physics (bank, pitch, speed)
- [ ] Atmospheric haze shader
- [ ] Sky gradient

### M2 — It Looks Good
- [ ] Normal mapped terrain
- [ ] Instanced trees (one type, no wind yet)
- [ ] Water plane with basic shader
- [ ] Time of day (moving sun, color temperature)
- [ ] God rays

### M3 — World Feels Alive
- [ ] Wind vertex shader on foliage
- [ ] Multiple tree/rock types per biome
- [ ] Cloud layer
- [ ] Billboard fallback for distant trees
- [ ] Weather (rain, fog variation)

### M4 — Things to Find
- [ ] Biome variety (visual differentiation)
- [ ] Points of interest (procedurally placed landmarks)
- [ ] Other birds / flocking behavior
- [ ] Thermals and soaring mechanics
