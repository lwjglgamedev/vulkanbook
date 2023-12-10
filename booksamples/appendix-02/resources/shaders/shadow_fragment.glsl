#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

struct Material {
    vec4 diffuseColor;
    int textureIdx;
    int normalMapIdx;
    int metalRoughMapIdx;
    float roughnessFactor;
    float metallicFactor;
};

layout (set = 1, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];
layout (std430, set=2, binding=0) readonly buffer srcBuf {
    Material data[];
} materialsBuf;
layout (location = 0) in vec2 inTextCoords;
layout (location = 1) in flat uint intMatIdx;

void main()
{
    Material material = materialsBuf.data[intMatIdx];
    float alpha = texture(textSampler[material.textureIdx], inTextCoords).a;
    if (alpha < 0.5) {
        discard;
    }
}