#version 450
#extension GL_EXT_buffer_reference: require
#extension GL_EXT_buffer_reference2: enable
#extension GL_EXT_scalar_block_layout: require

layout(location = 0) out vec4 outPos;
layout(location = 1) out vec3 outNormal;
layout(location = 2) out vec3 outTangent;
layout(location = 3) out vec3 outBitangent;
layout(location = 4) out vec2 outTextCoords;

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 matrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 matrix;
} viewUniform;

struct Vertex {
    vec3 inPos;
    vec3 inNormal;
    vec3 inTangent;
    vec3 inBitangent;
    vec2 inTextCoords;
};

layout(scalar, buffer_reference) buffer VertexBuffer {
    Vertex[] vertices;
};

layout(std430, buffer_reference) buffer IndexBuffer {
    uint[] indices;
};

layout(push_constant) uniform pc {
    mat4 modelMatrix;
    VertexBuffer vertexBuffer;
    IndexBuffer indexBuffer;
} push_constants;

void main()
{
    uint index = push_constants.indexBuffer.indices[gl_VertexIndex];
    VertexBuffer vertexData = push_constants.vertexBuffer;

    Vertex vertex = vertexData.vertices[index];
    vec3 inPos    = vertex.inPos;
    vec4 worldPos = push_constants.modelMatrix * vec4(inPos, 1);
    gl_Position   = projUniform.matrix * viewUniform.matrix * worldPos;
    mat3 mNormal  = transpose(inverse(mat3(push_constants.modelMatrix)));

    outPos        = worldPos;
    outNormal     = mNormal * normalize(vertex.inNormal);
    outTangent    = mNormal * normalize(vertex.inTangent);
    outBitangent  = mNormal * normalize(vertex.inBitangent);

    outTextCoords = vertex.inTextCoords;
}