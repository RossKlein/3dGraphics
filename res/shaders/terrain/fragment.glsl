#version 460 core

// ---- Inputs from vertex shader ---------------------------------------

in float vHeight;
in float vHumidity;
in vec3  vWorldNormal;
in float vDepth;
in float vNoiseVal;
in vec2  vWorldStepped;

out vec4 outColor;

// ---- PBR sand material (texture units 1-3) --------------------------

uniform sampler2D sandColTex;   // albedo  (COL_2K)
uniform sampler2D sandNrmTex;   // normals (NRM_2K), tangent-space
uniform sampler2D sandAoTex;    // ambient occlusion (AO_2K)

// Tile the texture every SAND_TILE world units.  Chosen so the grain is
// visible up close (LOD 0 cells = 1 unit) without looking too small far out.
const float SAND_TILE = 4.0;

// ---- Lighting / atmosphere uniforms ---------------------------------

uniform vec3  sunDir;
uniform float hazeStart;
uniform float hazeDensity;
uniform float uTimeOfDay;   // 0=midnight, 0.25=dawn, 0.5=noon, 0.75=dusk

// ---- Biome colour tables (matches BiomeMap.java) --------------------
// Index: 0=OCEAN 1=BEACH 2=PLAINS 3=GRASSLAND 4=FOREST 5=HILLS 6=MOUNTAINS 7=ALPINE

const vec3 COLOR_LO[8] = vec3[8](
    vec3(0.10, 0.25, 0.55),  // OCEAN
    vec3(0.82, 0.76, 0.55),  // BEACH
    vec3(0.60, 0.70, 0.30),  // PLAINS
    vec3(0.25, 0.55, 0.18),  // GRASSLAND
    vec3(0.12, 0.38, 0.12),  // FOREST
    vec3(0.45, 0.50, 0.25),  // HILLS
    vec3(0.50, 0.45, 0.38),  // MOUNTAINS
    vec3(0.85, 0.87, 0.90)   // ALPINE
);

const vec3 COLOR_HI[8] = vec3[8](
    vec3(0.08, 0.18, 0.45),  // OCEAN
    vec3(0.90, 0.85, 0.65),  // BEACH
    vec3(0.70, 0.75, 0.35),  // PLAINS
    vec3(0.30, 0.62, 0.22),  // GRASSLAND
    vec3(0.08, 0.28, 0.10),  // FOREST
    vec3(0.52, 0.56, 0.30),  // HILLS
    vec3(0.58, 0.52, 0.44),  // MOUNTAINS
    vec3(0.95, 0.96, 0.98)   // ALPINE
);

const vec3 ROCK_COLOR = vec3(0.42, 0.40, 0.36);
const vec3 SNOW_COLOR = vec3(0.95, 0.96, 0.98);

// World height thresholds (must match World.java)
const float SEA_LEVEL     =   0.0;
const float BEACH_LINE    =  20.0;
const float HILL_LINE     = 250.0;
const float MOUNTAIN_LINE = 600.0;
const float ALPINE_LINE   = 900.0;
const float SNOW_LINE     = 800.0;

// ---- Biome selection ------------------------------------------------

int biomeIndex(float h, float humidity) {
    if (h < SEA_LEVEL)      return 0;  // OCEAN
    if (h < BEACH_LINE)     return 1;  // BEACH
    if (h >= ALPINE_LINE)   return 7;  // ALPINE
    if (h >= MOUNTAIN_LINE) return 6;  // MOUNTAINS
    if (h >= HILL_LINE)     return 5;  // HILLS
    if (humidity < 0.30)    return 2;  // PLAINS
    if (humidity < 0.60)    return 3;  // GRASSLAND
    return 4;                          // FOREST
}

// ---- Horizon colour (matches sky/fragment.glsl exactly) ------------

vec3 horizonColor(float tod) {
    float dayF  = smoothstep(0.30, 0.48, tod) - smoothstep(0.52, 0.70, tod);
    float ddF   = max(smoothstep(0.18, 0.25, tod) - smoothstep(0.25, 0.40, tod),
                      smoothstep(0.60, 0.75, tod) - smoothstep(0.75, 0.82, tod));
    float nightF = 1.0 - clamp(dayF + ddF, 0.0, 1.0);

    vec3 horDay   = vec3(0.65, 0.85, 1.00);
    vec3 horDusk  = vec3(0.95, 0.38, 0.08);
    vec3 horNight = vec3(0.03, 0.03, 0.08);

    return horDay * dayF + horDusk * ddF + horNight * nightF;
}

// ---- Main -----------------------------------------------------------

void main() {
    int bi = biomeIndex(vHeight, vHumidity);

    // ---- Sand PBR textures (world-stable tiling) ------------------------
    vec2 sandUV = vWorldStepped * (1.0 / SAND_TILE);

    vec3  sandAlbedo = texture(sandColTex, sandUV).rgb;
    vec3  sandNrmRaw = texture(sandNrmTex, sandUV).rgb * 2.0 - 1.0;
    float sandAO     = texture(sandAoTex,  sandUV).r;

    // ---- Base biome colour ----------------------------------------------
    vec3 baseColor = mix(COLOR_LO[bi], COLOR_HI[bi], vNoiseVal);

    // Blend albedo texture into the biome colour:
    //   BEACH (1): sand dominates — most of what you see should be the texture
    //   OCEAN (0): subtle sand detail visible through shallow water tint
    //   others:    very faint grain detail only
    float sandBlend;
    if      (bi == 1) sandBlend = 0.85;
    else if (bi == 0) sandBlend = 0.25;
    else              sandBlend = 0.12;
    baseColor = mix(baseColor, sandAlbedo, sandBlend);

    // ---- Normal map: perturb geometric normal with surface detail -------
    // sandNrmRaw is tangent-space: x=rightward, y=forward, z=up.
    // For flat terrain tangent=(world X), bitangent=(world Z), normal=(world Y).
    // Blend the perturbation with the geometric normal:
    const float NRM_STRENGTH = 0.35;
    vec3 geomN    = normalize(vWorldNormal);
    vec3 detailN  = normalize(geomN + vec3(sandNrmRaw.x, 0.0, sandNrmRaw.y) * NRM_STRENGTH);

    // ---- Slope → rock darkening -----------------------------------------
    float slope = 1.0 - geomN.y;
    float sf    = clamp(slope * 1.8, 0.0, 1.0);
    baseColor   = mix(baseColor, ROCK_COLOR, sf);

    // ---- Snow blending --------------------------------------------------
    if (vHeight > SNOW_LINE) {
        float snowT = clamp((vHeight - SNOW_LINE) / 30.0, 0.0, 1.0);
        baseColor   = mix(baseColor, SNOW_COLOR, snowT);
    }

    // ---- Lighting (Phong diffuse + ambient, AO applied to ambient) ------
    float ambient = 0.28 * sandAO;
    float diffuse = max(dot(detailN, sunDir), 0.0) * 0.72;
    vec3  lit     = (ambient + diffuse) * baseColor;

    // ---- Atmospheric haze -----------------------------------------------
    float haze   = 1.0 - exp(-max(0.0, vDepth - hazeStart) * hazeDensity);
    vec3  result = mix(lit, horizonColor(uTimeOfDay), clamp(haze, 0.0, 1.0));

    outColor = vec4(result, 1.0);
}
