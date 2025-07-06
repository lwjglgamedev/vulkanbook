#version 450
layout (location = 0) in vec2 inTextCoord;

layout (location = 0) out vec4 outFragColor;

layout (set = 0, binding = 0) uniform sampler2D albedoSampler;

void main() {
    vec3 albedo = texture(albedoSampler, inTextCoord).rgb;
    outFragColor = vec4(albedo, 1.0);
}