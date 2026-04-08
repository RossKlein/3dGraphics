#version 330 core
in  vec2 uv;
out vec4 fragColor;

uniform vec2  uSunPos;     // sun UV in [0,1]
uniform float uIntensity;  // 0 when sun below horizon; fades the disc to black
uniform float uAspect;     // screenWidth / screenHeight — keeps the disc round

// Disc radius in aspect-corrected UV space.
// Intentionally larger than the visual sun disc to give the blur some source area.
const float SUN_RADIUS = 0.06;

void main() {
    vec2  d    = (uv - uSunPos) * vec2(uAspect, 1.0);
    float dist = length(d);
    float mask = 1.0 - smoothstep(SUN_RADIUS * 0.75, SUN_RADIUS, dist);
    fragColor  = vec4(vec3(mask * uIntensity), 1.0);
}
