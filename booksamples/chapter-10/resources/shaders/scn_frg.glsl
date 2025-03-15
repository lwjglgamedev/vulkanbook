#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

layout(location = 0) in vec2 inTextCoords;
layout(location = 1) in flat uint inMaterialIdx;

layout(location = 0) out vec4 outFragColor;

struct Material {
    vec4 diffuseColor;
    uint hasTexture;
    uint textureIdx;
    uint padding[2];
};

layout(set = 2, binding = 0) readonly buffer MaterialUniform {
    Material materials[];
} matUniform;

layout(set = 3, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];

void main()
{
    Material material = matUniform.materials[inMaterialIdx];
    if (material.hasTexture == 1) {
        outFragColor = texture(textSampler[material.textureIdx], inTextCoords);
    } else {
        outFragColor = material.diffuseColor;
    }
}

