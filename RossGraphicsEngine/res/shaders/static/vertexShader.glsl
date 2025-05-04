#version 400 core

layout (location = 0) in vec3 position;
layout (location = 1) in vec4 color;
layout (location = 2) in vec3 normal;
layout (location = 3) in vec2 uvCoordinate;

out vec4 passedColor;
out vec2 passedUvCoordinates;
out vec3 outnormals;
out vec3 outposition;
out vec3 lightpos;

uniform mat4 m;
uniform mat4 v;
uniform mat4 p;
uniform vec3 light;
uniform bool useFlatColor;


void main(void) {

    gl_Position =  p * v * m * vec4(position ,1);
    lightpos = light;

    mat3 normalMatrix = mat3(v*m);
    normalMatrix = inverse(normalMatrix);
    normalMatrix = transpose(normalMatrix);

    vec4 objectColor = vec4(1, 1, 1, 1);
    outnormals = normalize((normalMatrix* normal));

    outposition = vec3(v*m* vec4(position,1));
    passedColor = color;
    passedUvCoordinates = uvCoordinate;
}
