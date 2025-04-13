#version 460
#extension GL_EXT_buffer_reference : require
#extension GL_EXT_buffer_reference2 : enable
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

struct Vertex {
    vec3 inPos;
    vec3 inNormal;
    vec3 inTangent;
    vec3 inBitangent;
    vec2 inTextCoords;
};

layout(scalar, buffer_reference, buffer_reference_align=8) buffer VertexBuffer {
    Vertex vertices[];
};

layout(std430, buffer_reference, buffer_reference_align=8) buffer IndexBuffer {
    uint indices[];
};

struct InstanceData {
    mat4 modelMatrix;
    uint materialIdx;
};

layout(scalar, buffer_reference, buffer_reference_align=8) buffer InstancesDataBuffer {
    InstanceData[] instancesData;
};

layout(buffer_reference, scalar) buffer AddressBuffer { uint64_t addresses[]; };

layout(push_constant) uniform pc {
    uint64_t vertexBuffersArr;
    uint64_t indexBuffersAddr;
    InstancesDataBuffer instancesDataBuffer;
} push_constants;

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out flat uint outMaterialIdx;

void main()
{
    AddressBuffer vertexAddresses = AddressBuffer(push_constants.vertexBuffersArr);
    AddressBuffer indexAddresses = AddressBuffer(push_constants.indexBuffersAddr);

    VertexBuffer vertexBuffer = VertexBuffer(vertexAddresses.addresses[gl_DrawID]);
    IndexBuffer indexBuffer = IndexBuffer(indexAddresses.addresses[gl_DrawID]);

    uint index = indexBuffer.indices[gl_VertexIndex];
    Vertex vertex = vertexBuffer.vertices[index];

    InstancesDataBuffer instancesDataBuffer = push_constants.instancesDataBuffer;
    InstanceData instanceData = instancesDataBuffer.instancesData[gl_DrawID];

    vec3 inPos     = vertex.inPos;
    vec4 worldPos  = instanceData.modelMatrix * vec4(inPos, 1);
    outTextCoords  = vertex.inTextCoords;
    outMaterialIdx = instanceData.materialIdx;

    gl_Position    = worldPos;
}