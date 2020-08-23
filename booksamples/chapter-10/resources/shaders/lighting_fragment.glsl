#version 450

layout(location = 0) in vec2 inTextCoord;

layout(location = 0) out vec4 outFragColor;

layout(set = 0, binding = 0) uniform sampler2D albedoSampler;
layout(set = 0, binding = 1) uniform sampler2D depthSampler;

void main() {
    outFragColor = texture(albedoSampler, inTextCoord);
}