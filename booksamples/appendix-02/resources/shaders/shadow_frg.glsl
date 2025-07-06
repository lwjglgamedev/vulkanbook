#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

layout (location = 0) in vec2 inTextCoords;
layout (location = 1) in flat uint inMaterialIdx;

layout (location = 0) out vec2 outFragColor;

struct Material {
    vec4 diffuseColor;
    uint hasTexture;
    uint textureIdx;
    uint hasNormalMap;
    uint normalMapIdx;
    uint hasRoughMap;
    uint roughMapIdx;
    float roughnessFactor;
    float metallicFactor;
};

layout (set = 1, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];
layout (set = 2, binding = 0) readonly buffer MaterialUniform {
    Material materials[];
} matUniform;

void main()
{
    Material material = matUniform.materials[inMaterialIdx];
    vec4 albedo;
    if (material.hasTexture == 1) {
        albedo = texture(textSampler[material.textureIdx], inTextCoords);
    } else {
        albedo = material.diffuseColor;
    }
    if (albedo.a < 0.5) {
        discard;
    }

    float depth = gl_FragCoord.z;
    float moment1 = depth;
    float moment2 = depth * depth;

    // Adjust moments to avoid light bleeding
    float dx = dFdx(depth);
    float dy = dFdy(depth);
    moment2 += 0.25 * (dx * dx + dy * dy);

    outFragColor = vec2(moment1, moment2);
}