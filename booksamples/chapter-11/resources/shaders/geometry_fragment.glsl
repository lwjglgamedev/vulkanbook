#version 450

layout(location = 0) in vec3 inNormal;
layout(location = 1) in vec3 inTangent;
layout(location = 2) in vec3 inBitangent;
layout(location = 3) in vec2 inTextCoords;

layout(location = 0) out vec4 outAlbedo;
layout(location = 1) out vec4 outNormal;
layout(location = 2) out vec4 outPBR;

layout(set = 2, binding = 0) uniform sampler2D textSampler;
layout(set = 3, binding = 0) uniform sampler2D normalSampler;
layout(set = 4, binding = 0) uniform sampler2D metRoughSampler;

layout(set = 5, binding = 0) uniform MaterialUniform {
    vec4 diffuseColor;
    float hasTexture;
    float hasNormalMap;
    float hasMetalRoughMap;
    float roughnessFactor;
    float metallicFactor;
} material;

vec3 calcNormal(float hasNormalMap, vec3 normal, vec2 textCoords, mat3 TBN)
{
    vec3 newNormal = normal;
    if (hasNormalMap > 0)
    {
        newNormal = texture(normalSampler, textCoords).rgb;
        newNormal = normalize(newNormal * 2.0 - 1.0);
        newNormal = normalize(TBN * newNormal);
    }
    return newNormal;
}

void main()
{
    if (material.hasTexture > 0) {
        outAlbedo = texture(textSampler, inTextCoords);
    } else {
        outAlbedo = material.diffuseColor;
    }

    // Hack to avoid transparent PBR artifacts
    if (outAlbedo.a < 0.5) {
        discard;
    }

    mat3 TBN = mat3(inTangent, inBitangent, inNormal);
    vec3 newNormal = calcNormal(material.hasNormalMap, inNormal, inTextCoords, TBN);
    // Transform normals from [-1, 1] to [0, 1]
    outNormal = vec4(0.5 * newNormal + 0.5, 1.0);

    float ao = 0.5f;
    float roughnessFactor = 0.0f;
    float metallicFactor = 0.0f;
    if (material.hasMetalRoughMap > 0) {
        vec4 metRoughValue = texture(metRoughSampler, inTextCoords);
        roughnessFactor = metRoughValue.g;
        metallicFactor = metRoughValue.b;
    } else {
        roughnessFactor = material.roughnessFactor;
        metallicFactor = material.metallicFactor;
    }

    outPBR = vec4(ao, roughnessFactor, metallicFactor, 1.0f);
}