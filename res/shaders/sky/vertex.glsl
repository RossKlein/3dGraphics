#version 330 core
layout(location = 0) in vec2 aPos;
out vec2 uv;
void main() {
    uv = aPos * 0.5 + 0.5;
    // z = 1.0 so the sky sits at the far plane after perspective divide.
    // Depth writes are disabled on the Java side so this never occludes geometry.
    gl_Position = vec4(aPos, 1.0, 1.0);
}
