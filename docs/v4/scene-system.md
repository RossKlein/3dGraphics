# v4 Scene and Layer System

## Core Concept

The `Layer` replaces `Scene` as the fundamental unit of game logic and rendering. Multiple layers are active simultaneously, each contributing update jobs and render calls every frame.

A "scene" in the traditional sense (main menu, gameplay) is just a named set of layers pushed together. Transitioning between scenes means swapping layer stacks.

---

## Render Target Groups

Every layer belongs to one of two render target groups. This is the most important property a layer has, because it determines whether the layer goes through post-processing.

```
WORLD       → renders to offscreen FBO
                      ↓
              post-process chain
              (FBO → FBO → screen)
                      ↓
SCREEN      → renders directly to screen, on top of everything
```

**World-space layers** — 3D perspective camera, output to FBO, affected by all post-processing (god rays, bloom, tone mapping, atmospheric haze). Examples: sky, terrain, bird, world particles.

**Screen-space layers** — 2D orthographic camera (or no camera at all), render directly to the screen after post-processing. Never affected by world effects. Examples: HUD, pause menu, main menu, debug overlay.

The post-process chain is not a layer — it's a fixed pipeline stage between the two groups. It reads the world FBO and writes to the screen. How many shader passes it runs internally is invisible to the layer system.

---

## `Layer` Interface

```java
interface Layer {

    enum Group { WORLD, SCREEN }
    enum State { ACTIVE, PAUSED, HIDDEN }

    Group getGroup();          // WORLD or SCREEN — determines render target
    int getOrder();            // draw order within the group (lower = first)
    String getName();

    // Called by LayerManager when this layer is pushed
    List<Job> onPush(JobModule jobs);

    // Called by LayerManager when this layer is popped
    List<Job> onPop(JobModule jobs);

    // state == ACTIVE: both run
    // state == PAUSED: render runs, update does not
    // state == HIDDEN: neither runs
    List<Job> update(JobModule module);
    List<Job> render(JobModule module, RenderTarget target, double deltaTime);
}
```

The `RenderTarget` passed to `render()` is the appropriate FBO for WORLD layers, or the screen framebuffer for SCREEN layers. Layers don't manage their own render targets — the `LayerManager` handles that.

---

## `LayerManager`

Owns the active layer stack and drives the per-frame pipeline.

```java
class LayerManager {
    List<Layer> layers = new ArrayList<>();   // ordered by Layer.getOrder()

    void push(Layer layer);      // calls layer.onPush(), inserts in order
    void pop(Layer layer);       // calls layer.onPop(), removes
    void pop(String name);
    void setState(String name, Layer.State state);

    // Called each update frame:
    List<Job> collectUpdateJobs(JobModule jobs) {
        return layers.stream()
            .filter(l -> l.getState() == ACTIVE)
            .flatMap(l -> l.update(jobs).stream())
            .toList();
    }

    // Called each render frame — implements the two-group pipeline:
    void renderFrame(JobModule jobs, double dt) {
        // 1. Render all WORLD layers to world FBO
        worldFBO.bind();
        glClear(COLOR | DEPTH);
        layers.stream()
            .filter(l -> l.getGroup() == WORLD && l.getState() != HIDDEN)
            .sorted(by order)
            .forEach(l -> collectRenderJobs(l, worldFBO, jobs, dt));

        // 2. Run post-process chain: worldFBO → screen
        postProcessChain.run(worldFBO);

        // 3. Render all SCREEN layers directly to screen, on top
        glBindFramebuffer(DEFAULT);
        layers.stream()
            .filter(l -> l.getGroup() == SCREEN && l.getState() != HIDDEN)
            .sorted(by order)
            .forEach(l -> collectRenderJobs(l, screenTarget, jobs, dt));
    }
}
```

---

## Layer States

Layers have two logically independent concerns — updating and rendering — expressed as three states:

| State | Updates | Renders | Use case |
|-------|---------|---------|----------|
| `ACTIVE` | Yes | Yes | Normal |
| `PAUSED` | No | Yes | Game paused — world visible but frozen behind menu |
| `HIDDEN` | No | No | Layer exists but is completely inactive |

When the pause menu is opened:
```java
layerManager.setState("world", PAUSED);   // world freezes but stays visible
layerManager.push(new PauseMenuLayer());   // menu renders on top
```

When resumed:
```java
layerManager.pop("pause-menu");
layerManager.setState("world", ACTIVE);
```

---

## Particle Placement

Particles belong to whichever group matches their nature:

| Particle type | Group | Reason |
|--------------|-------|--------|
| Leaves, feathers, dust | WORLD | In 3D space, should have god rays and haze applied |
| Atmospheric haze particles | WORLD | Part of the sky/environment |
| UI sparkle, loading effects | SCREEN | 2D, should be crisp, not post-processed |

For this game, nearly all particles are world-space. A `ParticleLayer` (Group: WORLD) renders before the bird so particles can appear around it naturally.

---

## Shared Game State

Layers cannot be fully isolated — the HUD layer needs the bird's speed and altitude from the world layer. Rather than layers holding references to each other, they share a `GameState` object owned by the scene.

```java
class GameState {
    volatile Vec3f birdPosition;
    volatile float birdSpeed;
    volatile float birdAltitude;
    volatile float birdHeading;
    volatile float timeOfDay;
    // written by WorldLayer update jobs, read by HudLayer render jobs
}
```

Marked `volatile` for the same reason as `JobModule`'s shared fields in v3 — update thread writes, render thread reads, volatile provides the memory barrier.

---

## Scene Definitions

A scene is a method that pushes a set of layers:

```java
class Scenes {

    static void loadMainMenu(LayerManager lm, JobModule jobs) {
        lm.push(new MainMenuLayer());      // SCREEN, order 0
    }

    static void loadGameplay(LayerManager lm, JobModule jobs, GameState state) {
        lm.push(new SkyLayer(state));          // WORLD, order 0
        lm.push(new WorldLayer(state, jobs));  // WORLD, order 1 (terrain, bird, assets)
        lm.push(new ParticleLayer(state));     // WORLD, order 2
        lm.push(new HudLayer(state));          // SCREEN, order 0
    }

    static void pushPauseMenu(LayerManager lm) {
        lm.setState("world", PAUSED);
        lm.setState("hud", HIDDEN);
        lm.push(new PauseMenuLayer());         // SCREEN, order 10
    }

    static void popPauseMenu(LayerManager lm) {
        lm.pop("pause-menu");
        lm.setState("world", ACTIVE);
        lm.setState("hud", ACTIVE);
    }
}
```

Transitioning from menu to gameplay:
```java
lm.pop("main-menu");
Scenes.loadGameplay(lm, jobs, gameState);
```

No special "scene manager" class needed — it's just push/pop on the layer stack.

---

## Post-Process Chain

The post-process chain is not part of the layer system. It is a fixed pipeline stage in `LayerManager.renderFrame()`, between the world FBO and the screen.

```java
class PostProcessChain {
    List<PostProcessPass> passes;    // god rays, bloom, tone map, etc.
    Framebuffer[] pingPong;          // alternating FBOs for multi-pass

    void run(Framebuffer worldFBO) {
        Framebuffer input = worldFBO;
        for (PostProcessPass pass : passes) {
            Framebuffer output = nextPingPong();
            pass.run(input, output);
            input = output;
        }
        // blit final result to screen
        blit(input, screen);
    }
}
```

Each `PostProcessPass` is a fullscreen quad draw with its own shader. Passes can be added, removed, or reordered without touching the layer system.

---

## Layer Inventory (Planned)

| Layer | Group | Order | State when paused |
|-------|-------|-------|------------------|
| `SkyLayer` | WORLD | 0 | PAUSED |
| `WorldLayer` | WORLD | 1 | PAUSED |
| `ParticleLayer` | WORLD | 2 | PAUSED |
| `HudLayer` | SCREEN | 0 | HIDDEN |
| `PauseMenuLayer` | SCREEN | 10 | — (is the pause) |
| `MainMenuLayer` | SCREEN | 0 | — (separate scene) |
| `LoadingLayer` | SCREEN | 5 | — (transient) |
| `DebugLayer` | SCREEN | 99 | ACTIVE (always) |

---

## How This Connects to the Job System

Each active layer returns update jobs and render jobs, exactly as the current `Scene` interface does. `LayerManager.collectUpdateJobs()` merges them all into one list for the `JobQueue`.

The existing `JobModule.updateJobs()` and `JobModule.renderJobs()` calls are unchanged — they just call into the `LayerManager` instead of a single `Scene`.

---

## Open Questions

- [ ] Does `WorldLayer` own the `World` (chunks, instance batches) or does `World` live on `GameState`? The latter makes it accessible to the `HudLayer` for things like minimap rendering.
- [ ] Loading transition: does `WorldLayer` push itself after initial chunks are ready, or does a `LoadingLayer` block until `WorldLayer` reports ACTIVE?
- [ ] Should post-process passes be toggleable at runtime (e.g., disable bloom on low-end hardware)? Probably yes — a settings flag per pass.
- [ ] The flamegraph overlay: it currently renders as an overlay in the existing scene. It becomes `DebugLayer` (SCREEN, order 99) — always visible regardless of other layer states.
