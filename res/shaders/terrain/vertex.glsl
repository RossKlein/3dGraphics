#version 460 core

// ---- Clipmap constants -----------------------------------------------
// Must match World.RING_VERTS / RING_QUADS and TerrainRing's skirt constants.
const int NV = 256;   // vertices per side (= texture dimension)
const int NQ = 255;   // quads per side

// ---- Skirt vertex ID layout ------------------------------------------
//
// Regular grid: vid in [0, NV*NV)
//   ix = vid / NV,  iz = vid % NV
//
// Outer skirt: vid in [OUTER_BASE, OUTER_BASE + 4*NV)
//   4 edges × NV sunk copies, facing INWARD (toward ring center / player).
//   local = vid - OUTER_BASE,  edge = local / NV,  j = local % NV
//     edge 0 (top,    iz=0  ): ix=j,   iz=0
//     edge 1 (bottom, iz=NQ ): ix=j,   iz=NQ
//     edge 2 (left,   ix=0  ): ix=0,   iz=j
//     edge 3 (right,  ix=NQ ): ix=NQ,  iz=j
//
const int OUTER_BASE = NV * NV;   // 65536

// ---- Inputs ----------------------------------------------------------
// No vertex attributes — all geometry derived from gl_VertexID.

// ---- Uniforms --------------------------------------------------------

uniform sampler2D heightHumTex;  // RG32F: r = height, g = humidity (256×256)

uniform int   texOriginX;        // toroidal column offset: ring ix=0 → texture col texOriginX
uniform int   texOriginY;        // toroidal row offset:    ring iz=0 → texture row texOriginY

uniform vec2  ringCenter;        // camera-relative XZ of ring center
uniform float ringStep;          // world units per cell
uniform float camY;              // camera Y offset (jobs.position.y)

// World step-coordinates of ring cell (ix=0, iz=0) — used for world-stable noise.
uniform int   ringOriginStepX;
uniform int   ringOriginStepY;

uniform mat4 v;   // view matrix
uniform mat4 p;   // projection matrix

// ---- Outputs ---------------------------------------------------------

out float vHeight;
out float vHumidity;
out vec3  vWorldNormal;
out float vDepth;
out float vNoiseVal;
out vec2  vWorldStepped;  // absolute world XZ in world units, for texture UV

// ---- Toroidal fetch --------------------------------------------------

// Returns (height, humidity) for ring cell (sx, sz), clamped to ring boundary.
// Clamp prevents incorrect toroidal wrapping when sampling neighbours at the edge.
vec2 fetchHH(int sx, int sz) {
    sx = clamp(sx, 0, NQ);
    sz = clamp(sz, 0, NQ);
    ivec2 tc = ivec2((texOriginX + sx) & (NV - 1),
                     (texOriginY + sz) & (NV - 1));
    return texelFetch(heightHumTex, tc, 0).rg;
}

// ---- Main ------------------------------------------------------------

void main() {
    int vid = gl_VertexID;
    int ix, iz;
    float skirtOffset = 0.0;  // positive = sink down; zero for regular grid

    // ---- Decode vertex ID ------------------------------------------------
    if (vid < OUTER_BASE) {
        // Regular grid vertex
        ix = vid / NV;
        iz = vid % NV;
    } else {
        // Outer-perimeter skirt vertex (faces inward, toward the player)
        int local = vid - OUTER_BASE;
        int edge  = local / NV;
        int j     = local % NV;
        if      (edge == 0) { ix = j;   iz = 0;  }   // top    (iz=0)
        else if (edge == 1) { ix = j;   iz = NQ; }   // bottom (iz=NQ)
        else if (edge == 2) { ix = 0;   iz = j;  }   // left   (ix=0)
        else                { ix = NQ;  iz = j;  }   // right  (ix=NQ)
        // Depth proportional to cell size so coarser rings cover bigger height gaps.
        skirtOffset = ringStep * 4.0;
    }

    // ---- Sample height + humidity ----------------------------------------
    vec2 hh      = fetchHH(ix, iz);
    float h      = hh.r;
    float humidity = hh.g;

    // ---- Camera-relative world position ----------------------------------
    float wx = ringCenter.x + float(ix - NV / 2) * ringStep;
    float wz = ringCenter.y + float(iz - NV / 2) * ringStep;
    float wy = h + camY - skirtOffset;

    // ---- Normal from central differences (shared by skirt and grid) ------
    // Skirt vertices inherit the normal of their corresponding edge vertex,
    // which is good enough — skirts are only visible at near-zero angles.
    float hL = fetchHH(ix - 1, iz).r;
    float hR = fetchHH(ix + 1, iz).r;
    float hB = fetchHH(ix, iz - 1).r;
    float hF = fetchHH(ix, iz + 1).r;

    vec3 tx = vec3(2.0 * ringStep, hR - hL, 0.0);
    vec3 tz = vec3(0.0, hF - hB, 2.0 * ringStep);
    vec3 n  = normalize(cross(tz, tx));

    // ---- World-stable per-vertex noise -----------------------------------
    int wsX = ringOriginStepX + ix;
    int wsZ = ringOriginStepY + iz;
    vNoiseVal = fract(sin(float(wsX) * 127.1 + float(wsZ) * 311.7) * 43758.5453);

    // ---- Output ----------------------------------------------------------
    vec4 viewPos = v * vec4(wx, wy, wz, 1.0);
    gl_Position  = p * viewPos;

    vHeight      = h;
    vHumidity    = humidity;
    vWorldNormal = n;
    vDepth       = length(viewPos.xyz);
    // World-stable absolute coordinates: (wsX, wsZ) are in step-units,
    // multiply by ringStep to get world units.  Stable across ring scrolling.
    vWorldStepped = vec2(float(wsX), float(wsZ)) * ringStep;
}
