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

layout(scalar, buffer_reference, buffer_reference_align=16) buffer VertexBuffer {
    Vertex[] vertices;
};

layout(std430, buffer_reference, buffer_reference_align=16) buffer IndexBuffer {
    uint[] indices;
};

struct InstanceData {
    mat4 modelMatrix;
    uint materialIdx;
    uint padding[3];
};

layout(push_constant) uniform pc {
    mat4 modelMatrix;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
    uint materialIdx;
} push_constants;

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out flat uint outMaterialIdx;

void main()
{
    uint index = push_constants.indexBuffer.indices[gl_VertexIndex];
    VertexBuffer vertexData = push_constants.vertexBuffer;

    Vertex vertex = vertexData.vertices[index];

    outTextCoords  = vertex.inTextCoords;
    outMaterialIdx = push_constants.materialIdx;

    gl_Position = push_constants.modelMatrix * vec4(vertex.inPos, 1.0f);
}