#version 330 core
in  vec2 uv;
out vec4 fragColor;

uniform float uInvFovX;    // 1 / P.m00 = tan(fov/2) * aspect
uniform float uInvFovY;    // 1 / P.m11 = tan(fov/2)
uniform mat3  uViewRot;    // 3x3 rotation part of view matrix
uniform vec3  uSunDir;     // world-space direction toward sun
uniform float uTimeOfDay;  // 0-1: 0=midnight, 0.25=dawn, 0.5=noon

// tod: 0=midnight  0.25=dawn  0.5=noon  0.75=dusk
vec3 skyColor(vec3 ray, vec3 sun, float tod) {
    float dayF  = smoothstep(0.30, 0.48, tod)
                - smoothstep(0.52, 0.70, tod);
    float ddF   = max(smoothstep(0.18, 0.25, tod) - smoothstep(0.25, 0.40, tod),
                      smoothstep(0.60, 0.75, tod) - smoothstep(0.75, 0.82, tod));
    float nightF = 1.0 - clamp(dayF + ddF, 0.0, 1.0);

    vec3 zenDay   = vec3(0.06, 0.28, 0.80);
    vec3 horDay   = vec3(0.65, 0.85, 1.00);
    vec3 zenDusk  = vec3(0.08, 0.06, 0.18);
    vec3 horDusk  = vec3(0.95, 0.38, 0.08);
    vec3 zenNight = vec3(0.01, 0.01, 0.05);
    vec3 horNight = vec3(0.03, 0.03, 0.08);

    vec3 zenith  = zenDay * dayF + zenDusk * ddF + zenNight * nightF;
    vec3 horizon = horDay * dayF + horDusk * ddF + horNight * nightF;

    // Below the horizon: clamp y to 0, so mix() outputs the pure horizon colour.
    // This lets the terrain's depth haze blend seamlessly into the sky background
    // instead of cutting to a hard ground colour.
    float h  = clamp(ray.y, 0.0, 1.0);
    vec3 sky = mix(horizon, zenith, h);

    vec3 sunCol = mix(vec3(1.0, 0.50, 0.15), vec3(1.0, 0.97, 0.80), dayF);

    float cosA = dot(ray, sun);
    float disc = smoothstep(0.9994, 0.9998, cosA);
    sky += disc * sunCol * 3.5 * step(0.0, sun.y);

    float halo = smoothstep(0.975, 0.999, cosA) * (1.0 - disc);
    sky += halo * sunCol * 0.45 * step(0.0, sun.y);

    return sky;
}

void main() {
    vec2 ndc     = uv * 2.0 - 1.0;
    vec3 viewRay = normalize(vec3(ndc.x * uInvFovX,
                                  ndc.y * uInvFovY,
                                  -1.0));
    // Rotate to world space: invView = transpose(uViewRot)
    vec3 worldRay = normalize(transpose(uViewRot) * viewRay);

    fragColor = vec4(skyColor(worldRay, uSunDir, uTimeOfDay), 1.0);
}
