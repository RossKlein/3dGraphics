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

void main(void) {

    gl_Position = p*vec4(position ,1.0);
    lightpos = (vec4(light, 1)).xyz;

    vec4 objectColor = vec4(1, 1, 1, 1);
  outnormals = (vec4(normalize(normal),0)).xyz;
    //outnormals = normalize(normals);
    outposition = (vec4(position, 0)).xyz;
    passedColor = color;
    passedUvCoordinates = uvCoordinate;
}
