# v4 Flight Physics

## Architecture Split

Two systems, one responsibility each:

**Bullet Physics** — collision only. It knows the shape of the world and tells us when the bird intersects something. It does not drive the bird's movement.

**AirPhysics** — movement only. It integrates aerodynamic forces (lift, drag, gravity, thrust, thermals) to produce a velocity and position each frame. It does not know about geometry.

The integration point: each frame AirPhysics computes a desired new position. Before committing that position, we run a Bullet sweep test. If the sweep hits something, we slide along the surface (terrain landing) or reflect (crash). The resolved position goes into `GameState`.

```
player input
     ↓
FlightController  →  AirPhysics (integrates forces → desired pos/vel)
                          ↓
                   Bullet sweep test  ←  PhysicsWorld (terrain + props)
                          ↓
                   resolved position → GameState.birdPosition
```

---

## Bullet Physics Integration

### Dependency

LWJGL ships official Bullet3 bindings. Add to your build:

```
org.lwjgl:lwjgl-bullet          (Java bindings)
org.lwjgl:lwjgl-bullet:natives  (native lib for your platform)
```

No extra CMake or submodule work — Bullet is a first-class LWJGL artifact.

### PhysicsWorld wrapper

A single `PhysicsWorld` instance is created at game startup and passed into every layer that needs collision (terrain layers add/remove shapes, bird layer runs sweep tests).

```java
class PhysicsWorld {

    private btDefaultCollisionConfiguration config;
    private btCollisionDispatcher           dispatcher;
    private btDbvtBroadphase                broadphase;
    private btSequentialImpulseConstraintSolver solver;
    private btDiscreteDynamicsWorld         world;

    public PhysicsWorld() {
        config     = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(config);
        broadphase = new btDbvtBroadphase();
        solver     = new btSequentialImpulseConstraintSolver();
        world      = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0, -20f, 0));  // tuned for flight feel
    }

    // Called once per update tick — advances simulation
    public void step(float dt) {
        world.stepSimulation(dt, 10, 1f / 120f);
    }

    // Sweep a sphere from 'from' to 'to', returns hit fraction (0-1) or 1.0 = no hit
    public float sweepSphere(float radius, Vector3 from, Vector3 to) { ... }

    // Add/remove rigid bodies (called by TerrainLayer as chunks load/unload)
    public void addBody(btRigidBody body)    { world.addRigidBody(body); }
    public void removeBody(btRigidBody body) { world.removeRigidBody(body); }

    public void dispose() { /* delete all Bullet objects in reverse order */ }
}
```

### Thread safety

Bullet is **not thread-safe**. All Bullet calls (step, sweep tests, add/remove bodies) must happen on the **update thread**. Never call Bullet from the render thread or from worker job threads.

The update thread already owns `PhysicsWorld.step()`. Sweep tests run in the same update tick, before writing to `GameState`.

---

## Collision Shapes

### Terrain — heightfield shape

Bullet's `btHeightfieldTerrainShape` takes a float[] array directly. This is exactly what `HeightmapGenerator` produces — no conversion needed.

```java
// Called by TerrainLayer when a chunk finishes generating (still on update thread)
public btRigidBody buildTerrainBody(float[] heights, int sizeX, int sizeZ,
                                     float chunkWorldX, float chunkWorldZ) {
    btHeightfieldTerrainShape shape = new btHeightfieldTerrainShape(
        sizeX, sizeZ,
        heights,
        1f,          // height scale
        minHeight, maxHeight,
        1,           // up axis = Y
        false        // flip quad edges
    );
    shape.setLocalScaling(new Vector3(CHUNK_WORLD_SIZE / sizeX, 1f, CHUNK_WORLD_SIZE / sizeZ));

    btRigidBody body = makeStaticBody(shape);
    body.setWorldTransform(/* center of chunk */);
    return body;
}
```

Chunk bodies are added to `PhysicsWorld` when a chunk reaches `LOADED` state and removed when it unloads. Because the heightfield pointer is owned by Java (the float[]), keep a reference to it alongside the body to prevent GC.

### Props (trees, rocks) — convex hull or primitives

For gameplay purposes, approximate shapes are fine:

| Prop | Collision shape |
|------|----------------|
| Tree trunk | `btCapsuleShape(radius=0.3, height=6)` |
| Rock / boulder | `btSphereShape(radius ≈ visual size * 0.8)` |
| Cliff face | `btBvhTriangleMeshShape` from actual mesh |
| Water surface | None (bird passes through) |

Static props use zero-mass rigid bodies. `AssetPlacer` creates them when a chunk loads, same lifecycle as terrain bodies.

### Bird — kinematic sphere

The bird is a **kinematic rigid body** — we control its position entirely via `AirPhysics` and use Bullet only for collision *queries*, not simulation.

A sphere with radius ≈ 0.6m (roughly bird wingspan / 4):

```java
btSphereShape  birdShape = new btSphereShape(0.6f);
btRigidBody    birdBody  = makeKinematicBody(birdShape);
// Mass = 0, collision flags = CF_KINEMATIC_OBJECT | CF_NO_CONTACT_RESPONSE
// The CF_NO_CONTACT_RESPONSE flag means Bullet detects but does not resolve
// collisions — AirPhysics handles the response itself via sweep tests.
```

Each frame we do a **convex sweep** from current position to desired position. The sweep returns a hit fraction and hit normal. AirPhysics uses this to slide along the surface or trigger a crash.

---

## AirPhysics Model

### Force breakdown

Every update tick, AirPhysics integrates five forces:

```
netForce = thrust + lift + drag + gravity + thermal
velocity += netForce * dt
desiredPosition = birdPosition + velocity * dt
```

#### Gravity
Constant, always acts. Tuned to feel good for a bird, not physically accurate.
```
gravity = (0, -9.8 * GRAVITY_SCALE, 0)   // GRAVITY_SCALE ≈ 0.5 for floaty bird feel
```

#### Thrust (flapping)
Input-driven. Holding the flap button applies a burst of thrust in the current forward direction. Has a flap cooldown (can't flap continuously). Thrust falls off quickly — birds don't sustain thrust, they flap to gain speed then glide.
```
if (flapping && flapCooldown <= 0):
    velocity += forward * FLAP_IMPULSE
    flapCooldown = FLAP_INTERVAL
```

#### Drag
Opposes velocity, proportional to speed². Caps the bird's maximum glide speed naturally.
```
dragForce = -normalize(velocity) * DRAG_COEFFICIENT * |velocity|²
```

Two drag coefficients — one for gliding (wings out, low drag) and one for diving (wings tucked, higher drag removed, bird accelerates faster). Player toggles between them.

#### Lift
Generated by forward speed. Acts perpendicular to velocity (upward component). Low speed = stall. High speed = sustained lift.
```
liftMagnitude = LIFT_COEFFICIENT * |velocity|²
liftForce = up * liftMagnitude * clamp(|velocity| / STALL_SPEED, 0, 1)
```

`STALL_SPEED` ≈ 5 m/s — below this, lift vanishes and the bird descends rapidly. Creates natural incentive to maintain speed.

#### Thermals
Rising air columns above sun-warmed terrain types (plains, rocks, dark soil). When the bird enters a thermal column, a vertical force is added. The bird can soar — gaining altitude without flapping.

```
for each activeThermal in world:
    dist = horizontal distance from bird to thermal center
    if dist < thermal.radius:
        influence = 1 - (dist / thermal.radius)   // linear falloff
        liftForce.y += thermal.strength * influence
```

`GameState` exposes a `List<Thermal> nearbyThermals` updated by `WorldLayer` when terrain chunks load. Thermals are data objects, not physics bodies.

---

## Banking and Orientation

The bird's orientation is derived from velocity — it always points where it's going. Roll (bank angle) is added on top based on turn rate.

```java
// Forward direction = normalized velocity
Vec3f forward = velocity.normalized();

// Compute yaw change this frame
float yawDelta = ...from input or velocity change...

// Bank angle proportional to yaw rate, fades out when turning stops
bankAngle = lerp(bankAngle, yawDelta * BANK_SCALE, dt * BANK_RESPONSE);
bankAngle = clamp(bankAngle, -MAX_BANK, MAX_BANK);

// Build orientation quaternion: forward direction + bank roll
Quaternion orientation = Quaternion.lookRotation(forward, Vec3f.UP)
                           .multiply(Quaternion.axisAngle(forward, bankAngle));
```

`GameState.birdOrientation` (or equivalent) stores this quaternion. `FlightCamera` and the animation system both read it — the camera tilts with the bird on turns, the wing animation drives off bank angle.

---

## Per-Frame Integration Order

All of this runs on the **update thread** inside `BirdLayer.update()`:

```
1. FlightController.read(inputHandler)
       → sets: throttle, pitchInput, yawInput, flapPressed, divingMode

2. AirPhysics.integrate(dt)
       → reads:  velocity, position, orientation, nearbyThermals (from GameState)
       → writes: desiredVelocity, desiredPosition

3. PhysicsWorld.sweepSphere(birdRadius, currentPos, desiredPos)
       → returns: hitFraction, hitNormal

4. AirPhysics.resolveCollision(hitFraction, hitNormal)
       → if hitFraction < 1.0:
           slide along surface normal (landing/grazing)
           if impact speed > CRASH_THRESHOLD: trigger crash state

5. PhysicsWorld.step(dt)
       → advances prop/debris simulation (not the bird — it's kinematic)

6. GameState.birdPosition  = resolvedPosition
   GameState.birdVelocity  = resolvedVelocity
   GameState.birdSpeed     = |resolvedVelocity|
   GameState.birdAltitude  = resolvedPosition.y - terrainHeightAt(x, z)
   GameState.birdOrientation = orientation
```

`GameState` writes are `volatile`, visible to the render thread on the next frame.

---

## Package Layout

```
Ross/Modules/
  physics/
    PhysicsWorld.java       — Bullet dynamics world, sweep tests, body lifecycle
    CollisionGroups.java    — bit flags (TERRAIN=1, PROP=2, BIRD=4, WATER=8)
    TerrainBodyBuilder.java — heightfield shape → btRigidBody per chunk

  flight/
    AirPhysics.java         — force integration, stall, bank, collision response
    FlightController.java   — raw input → AirPhysics inputs (throttle, yaw, etc.)
    FlightCamera.java       — third-person follow camera with lag and tilt
    Thermal.java            — data: position, radius, strength, terrain type
```

`PhysicsWorld` is created in `Main` alongside `GameState` and passed to layers that need it:

```java
PhysicsWorld physics = new PhysicsWorld();
GameState    state   = new GameState();

lm.push(new TerrainLayer(state, physics), jobs);   // adds/removes chunk bodies
lm.push(new BirdLayer(state, physics),   jobs);    // runs AirPhysics + sweep tests
lm.push(new HudLayer(state),             jobs);    // reads state only
```

---

## Open Questions

- [ ] Thermal placement: rule-based (dark terrain + sun angle) or hand-authored per biome?
- [ ] Crash behaviour: respawn at last safe position, or a feather-scatter death animation?
- [ ] `GRAVITY_SCALE` and `LIFT_COEFFICIENT` tuning — these need play-testing, not math
- [ ] Flock collision: do other birds have Bullet bodies, or is flocking pure visual?
- [ ] Water landing: soft collision (splash, float) vs hard collision (terrain-equivalent)
