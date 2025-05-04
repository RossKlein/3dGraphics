#version 400 core

in vec4 passedColor;
in vec2 passedUvCoordinates;
in vec3 outnormals;
in vec3 outposition;
in vec3 lightpos;

out vec4 outColor;

uniform sampler2D textureSampler;
uniform bool useFlatColor;


void main(void) {

    float ambientStrength = 0.2;
    vec3 lightColor = vec3(1, 1, 1);
    vec3 ambient = ambientStrength * lightColor;
    vec3 normal = outnormals;
    vec3 lightDirection = normalize(lightpos - outposition);
    vec3 diffuse = max(dot(normal, lightDirection), 0) * lightColor ;

    vec3 color = normal * 0.5 + 0.5;
    vec3 result = (ambient + diffuse)* vec3(color);
    //outColor = vec4(result, 1) * texture(textureSampler, passedUvCoordinates);
    if (useFlatColor){
        outColor = passedColor;
    }else {
        outColor = vec4(result, 1);
    }

}
