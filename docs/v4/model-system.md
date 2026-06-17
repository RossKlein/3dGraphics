# v4 Model System

The model system is covered in detail in [entity-system.md](entity-system.md).

## Quick Reference

| Concept | Class | Doc section |
|---------|-------|-------------|
| Base class for all world objects | `GameObject` | entity-system.md — The Base |
| Procedural terrain tile | `TerrainChunk` | entity-system.md — Category 1 |
| OBJ-loaded unique object | `Prop` | entity-system.md — Category 2 |
| Thousands of repeated objects | `InstanceBatch` | entity-system.md — Category 3 |
| Sky, water, atmosphere | `Sky`, `Water` | entity-system.md — Category 4 |
| Player / moving objects | `Bird`, `DynamicEntity` | entity-system.md — Category 5 |
| Position / rotation / scale | `Transform` | entity-system.md — Transform |
| Shared OBJ model across Props | `ModelCache` | entity-system.md — ModelCache |
| Procedural geometry arrays | `MeshBuilder` | entity-system.md — Category 1, 4 |
| Raw arrays → GPU VAO | `ModelBuilder` | unchanged from v3 |
| Draw order | Render layers 0–8 | entity-system.md — Render Layer Table |
