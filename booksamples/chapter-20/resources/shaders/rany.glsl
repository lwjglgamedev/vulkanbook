#version 460

#extension GL_EXT_ray_tracing : enable
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_scalar_block_layout : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
#extension GL_EXT_buffer_reference2 : require

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;
const int STRIDE = 14;

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

struct MeshData {
    uint materialIdx;
    uint padding0;
    uint64_t vtxAddress;
    uint64_t idxAddress;
};

struct Vertex {
  vec3 pos;
  vec3 normal;
  vec3 tangent;
  vec3 bitangent;
  vec2 uv;
};

struct hitPayload
{
  vec3 hitValue;
  uint seed;
  vec3 barycentrics;
  vec2 textCoord;
};

layout(location = 0) rayPayloadInEXT hitPayload prd;
layout(buffer_reference, scalar) buffer Vertices {float v[]; }; // Positions of an object
layout(buffer_reference, scalar) buffer Indices {uint i[]; }; // Triangle indices

layout(set=4, binding=0) readonly buffer srcBuf {
   MeshData data[];
} meshes;

layout(set=5, binding=0) readonly buffer srcMat {
   Material data[];
} materials;

layout(set=6, binding=0) uniform sampler2D textSampler[MAX_TEXTURES];

hitAttributeEXT vec2 attribs;

Vertex unpack(uint index, Vertices vertices) {
    uint offset = index * STRIDE;
    Vertex result;
    result.pos = vec3(vertices.v[offset], vertices.v[offset + 1], vertices.v[offset + 2]);
    offset += 3;
    result.normal = vec3(vertices.v[offset], vertices.v[offset + 1], vertices.v[offset + 2]);
    offset += 3;
    result.tangent = vec3(vertices.v[offset], vertices.v[offset + 1], vertices.v[offset + 2]);
    offset += 3;
    result.bitangent = vec3(vertices.v[offset], vertices.v[offset + 1], vertices.v[offset + 2]);
    offset += 3;
    result.uv = vec2(vertices.v[offset], vertices.v[offset + 1]);
    return result;
}

// Generate a random unsigned int in [0, 2^24) given the previous RNG state
// using the Numerical Recipes linear congruential generator
uint lcg(inout uint prev)
{
  uint LCG_A = 1664525u;
  uint LCG_C = 1013904223u;
  prev       = (LCG_A * prev + LCG_C);
  return prev & 0x00FFFFFF;
}

// Generate a random float in [0, 1) given the previous RNG state
float rnd(inout uint prev)
{
  return (float(lcg(prev)) / float(0x01000000));
}

void main() {
    int offset = gl_InstanceCustomIndexEXT + gl_GeometryIndexEXT;
    MeshData meshData = meshes.data[offset];
    Vertices vertices = Vertices(meshData.vtxAddress);
    Indices  indices  = Indices(meshData.idxAddress);
    Material material = materials.data[meshData.materialIdx];

    // Indices of the triangle
    uint triIndex = gl_PrimitiveID * 3;
    ivec3 ind = ivec3(indices.i[triIndex], indices.i[triIndex + 1], indices.i[triIndex + 2]);

    // Vertex of the triangle
    Vertex v0 = unpack(ind.x, vertices);
    Vertex v1 = unpack(ind.y, vertices);
    Vertex v2 = unpack(ind.z, vertices);

    const vec3 barycentrics = vec3(1.0 - attribs.x - attribs.y, attribs.x, attribs.y);
    vec2 textCoord = v0.uv * barycentrics.x + v1.uv * barycentrics.y + v2.uv * barycentrics.z;

    prd.barycentrics = barycentrics;
    prd.textCoord = textCoord;

    vec4 albedo = texture(textSampler[material.textureIdx], textCoord);
    if (albedo.a == 1.0) {
        return;
    } else if (albedo.a == 0.0) {
        ignoreIntersectionEXT;
    } else if (rnd(prd.seed) > albedo.a) {
        ignoreIntersectionEXT;
    } else {
        return;
    }
}