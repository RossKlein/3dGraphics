package Ross.Modules.scene;

import Ross.Modules.math.Vec3f;

/**
 * Shared mutable state that crosses layer boundaries.
 *
 * Layers cannot hold direct references to each other — for example, HudLayer
 * needs the bird's altitude that WorldLayer computes. GameState is the
 * single agreed-upon place for that cross-layer data.
 *
 * All fields are {@code volatile} to provide the memory barrier between the
 * update thread (writer) and render thread (reader), matching the same pattern
 * used for {@code JobModule.perspective}, {@code .modelview}, etc.
 *
 * One GameState instance is created per "gameplay session" and passed to every
 * layer that needs it via their constructor. It lives on the scene, not on
 * JobModule, so it is naturally scoped to the game being played rather than
 * the engine lifetime.
 */
public class GameState {

    // ---- Bird ---------------------------------------------------------------
    // Written by WorldLayer / FlightPhysics update jobs.
    // Read by HudLayer, SkyLayer (sun direction relative to bird), ParticleLayer.

    public volatile Vec3f birdPosition = new Vec3f(0, 100, 0);
    public volatile Vec3f birdVelocity = new Vec3f(0, 0, 0);
    public volatile float birdSpeed    = 0f;     // m/s, magnitude of velocity
    public volatile float birdAltitude = 100f;   // metres above terrain
    public volatile float birdHeading  = 0f;     // degrees, 0 = north, clockwise

    // ---- Environment --------------------------------------------------------
    // Written by WorldLayer. Read by sky, haze, and atmosphere shaders.

    public volatile float  timeOfDay    = 0.25f;       // 0–1: 0=midnight, 0.25=dawn, 0.5=noon
    public volatile String currentBiome = "grasslands"; // biome tag for ambient sound / tint
    public volatile float  windStrength = 0.3f;        // 0–1, drives foliage vertex shader
    public volatile float  windAngle    = 0f;          // degrees
}
