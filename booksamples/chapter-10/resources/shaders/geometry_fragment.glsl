#version 450

layout(location = 0) in vec3 inNormal;
layout(location = 1) in vec2 inTextCoords;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

layout(set = 2, binding = 0) uniform sampler2D textSampler;

layout(set = 3, binding = 0) uniform MaterialUniform {
    vec4 diffuseColor;
} material;

void main()
{
    outAlbedo = material.diffuseColor + texture(textSampler, inTextCoords);
    outNormal = vec4(inNormal, 1.0);
    outPBR = vec4(inNormal, 1.0);
}