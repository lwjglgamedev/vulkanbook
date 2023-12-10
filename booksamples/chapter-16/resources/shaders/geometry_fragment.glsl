#version 450

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

layout(location = 0) in vec3 inNormal;
layout(location = 1) in vec3 inTangent;
layout(location = 2) in vec3 inBitangent;
layout(location = 3) in vec2 inTextCoords;
layout(location = 4) flat in uint inMatIdx;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

struct Material {
    vec4 diffuseColor;
    int textureIdx;
    int normalMapIdx;
    int metalRoughMapIdx;
    float roughnessFactor;
    float metallicFactor;
};

layout (std430, set=2, binding=0) readonly buffer srcBuf {
    Material data[];
} materialsBuf;
layout(set = 3, binding = 0) uniform sampler2D textSampler[MAX_TEXTURES];

vec4 calcAlbedo(Material material) {
    outAlbedo = material.diffuseColor;
    if (material.textureIdx >= 0) {
        outAlbedo = texture(textSampler[material.textureIdx], inTextCoords);
    }
    return outAlbedo;
}

vec3 calcNormal(Material material, vec3 normal, vec2 textCoords, mat3 TBN) {
    vec3 newNormal = normal;
    if (material.normalMapIdx >= 0) {
        newNormal = texture(textSampler[material.normalMapIdx], textCoords).rgb;
        newNormal = normalize(newNormal * 2.0 - 1.0);
        newNormal = normalize(TBN * newNormal);
    }
    return newNormal;
}

vec2 calcRoughnessMetallicFactor(Material material, vec2 textCoords) {
    float roughnessFactor = 0.0f;
    float metallicFactor = 0.0f;
    if (material.metalRoughMapIdx >= 0) {
        vec4 metRoughValue = texture(textSampler[material.metalRoughMapIdx], textCoords);
        roughnessFactor = metRoughValue.g;
        metallicFactor = metRoughValue.b;
    } else {
        roughnessFactor = material.roughnessFactor;
        metallicFactor = material.metallicFactor;
    }

    return vec2(roughnessFactor, metallicFactor);
}

void main()
{
    Material material = materialsBuf.data[inMatIdx];
    outAlbedo = calcAlbedo(material);

    // Hack to avoid transparent PBR artifacts
    if (outAlbedo.a < 0.5) {
        discard;
    }

    mat3 TBN = mat3(inTangent, inBitangent, inNormal);
    vec3 newNormal = calcNormal(material, inNormal, inTextCoords, TBN);
    // Transform normals from [-1, 1] to [0, 1]
    outNormal = vec4(0.5 * newNormal + 0.5, 1.0);

    float ao = 0.5f;
    vec2 roughmetfactor = calcRoughnessMetallicFactor(material, inTextCoords);

    outPBR = vec4(ao, roughmetfactor.x, roughmetfactor.y, 1.0f);
}