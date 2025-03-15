#version 460
#extension GL_EXT_ray_tracing : enable
#extension GL_EXT_nonuniform_qualifier : enable
#extension GL_EXT_scalar_block_layout : enable
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require
#extension GL_EXT_buffer_reference2 : require
//#extension GL_EXT_debug_printf : enable

// Keep in sync manually with Java code
const int MAX_TEXTURES = 100;

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
  vec2 uv;
};

struct hitPayload
{
  vec3 hitValue;
  uint seed;
  vec3 barycentrics;
  vec2 textCoord;
};

layout(buffer_reference, scalar) buffer Vertices {vec4 v[]; }; // Positions of an object
layout(buffer_reference, scalar) buffer Indices {uint i[]; }; // Triangle indices

layout(set=4, binding=0) readonly buffer srcBuf {
   MeshData data[];
} meshes;

layout(set=5, binding=0) readonly buffer srcMat {
   Material data[];
} materials;

layout(set=6, binding=0) uniform sampler2D textSampler[MAX_TEXTURES];

layout(location = 0) rayPayloadInEXT hitPayload prd;
hitAttributeEXT vec2 attribs;

void main() {
    int offset = gl_InstanceCustomIndexEXT + gl_GeometryIndexEXT;
    MeshData meshData = meshes.data[offset];
    Material material = materials.data[meshData.materialIdx];

    prd.hitValue = texture(textSampler[material.textureIdx], prd.textCoord).rgb;
}