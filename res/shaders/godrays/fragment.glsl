#version 330 core
in  vec2 uv;
out vec4 fragColor;

uniform sampler2D uScene;      // texture unit 0: rendered scene
uniform sampler2D uOcclusion;  // texture unit 1: sun disc mask (white disc on black)
uniform vec2      uSunPos;     // sun UV in [0,1]²
uniform float     uIntensity;  // 0 when sun below horizon
uniform vec3      uSunColor;   // warm orange at dawn, pale yellow-white at noon

// ---- Tuning ---------------------------------------------------------------
//
// NUM_SAMPLES — march steps per ray.  100 is smooth and cheap; go higher
// (150–200) if shafts look banded/choppy.
const int   NUM_SAMPLES = 200;

// DENSITY — fraction of the pixel→sun path covered each march (0 < x ≤ 1).
//   1.0 = march all the way to the sun.
//   0.9 = march 90% of the way (slight shortfall keeps very-near pixels from
//         over-sampling the disc).
const float DENSITY     = .95;

// WEIGHT — brightness added per sample.  Scales with the corona intensity at
//   that step.  Too high → blowout; too low → invisible.  Try 0.005–0.020.
const float WEIGHT      = 0.012;

// DECAY — illumination multiplier applied each step (0 < x ≤ 1).
//   1.0 = flat brightness along the whole shaft.
//   0.98 = shafts fade smoothly with distance from the current pixel.
const float DECAY       = .98;

// EXPOSURE — final linear multiplier on all rays before adding to scene.
//   The easiest "master brightness" knob.
const float EXPOSURE    = 1.2;

// SUN_SPREAD — controls the angular width of the sun's light-source region
//   in screen space.  The corona is exp(-dist * SUN_SPREAD).
//   Low  (3–5)  = wide soft corona, rays visible even when far from sun.
//   High (10–20) = tight, only pixels very close to the sun disc see rays.
//   Recommended starting point: 5.0–7.0.
const float SUN_SPREAD  = 6.0;

// MAX_RAYS — hard brightness cap per channel after all samples accumulate.
//   Prevents blowout when many bright corona samples pile up.
//   0.20 = subtle.  0.40 = cinematic.  0.60 = dramatic/sunrise look.
const float MAX_RAYS    = 0.35;

// SKY_LO / SKY_HI — scene luminance thresholds for sky vs terrain detection.
//   Pixels below SKY_LO are treated as 100% terrain (block light).
//   Pixels above SKY_HI are treated as 100% sky (let light through).
//   The band [LO, HI] is a smooth transition.
//   Raise SKY_LO if lit terrain mistakenly passes as sky.
//   Lower SKY_LO if dark parts of the sky are being clipped.
const float SKY_LO      = 0.45;
const float SKY_HI      = 0.72;

// Perceptual luminance weights (ITU-R BT.601).
const vec3  LUM_W       = vec3(0.299, 0.587, 0.114);
// ---------------------------------------------------------------------------

void main() {
    // Erkaman-style radial march.
    // delta = step from uv toward uSunPos, covering DENSITY of the total
    // pixel→sun distance in NUM_SAMPLES steps.
    vec2  delta    = (uv - uSunPos) * (DENSITY / float(NUM_SAMPLES));
    vec2  sampleUv = uv;
    float illum    = 1.0;
    vec3  rays     = vec3(0.0);

    for (int i = 0; i < NUM_SAMPLES; i++) {
        sampleUv -= delta;                        // step toward sun
        vec2 s    = clamp(sampleUv, 0.0, 1.0);

        // Sky vs terrain gate.
        // Terrain pixels (low luminance) contribute no light — they occlude.
        float lum    = dot(texture(uScene, s).rgb, LUM_W);
        float isSky  = smoothstep(SKY_LO, SKY_HI, lum);

        // Sun corona: exponential falloff from the sun's screen position.
        // Only sky *near* the sun contributes shaft brightness; the rest of
        // the bright blue dome does not — preventing global over-brightening.
        float corona = exp(-length(s - uSunPos) * SUN_SPREAD);

        rays  += isSky * corona * illum * WEIGHT * uSunColor;
        illum *= DECAY;
    }

    rays = min(rays, vec3(MAX_RAYS)) * EXPOSURE * uIntensity;
    fragColor = texture(uScene, uv) + vec4(rays, 0.0);
}
