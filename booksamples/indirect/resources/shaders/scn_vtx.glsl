#version 450

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec3 inTangent;
layout(location = 3) in vec3 inBitangent;
layout(location = 4) in vec2 inTextCoords;

// Instanced attributes
layout (location = 5) in mat4 inModelMatrix;
layout (location = 9) in uint inMatIdx;

layout(location = 0) out vec4 outPos;
layout(location = 1) out vec3 outNormal;
layout(location = 2) out vec3 outTangent;
layout(location = 3) out vec3 outBitangent;
layout(location = 4) out vec2 outTextCoords;
layout(location = 5) flat out uint outMatIdx;

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 matrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 matrix;
} viewUniform;

void main()
{
    mat3 mNormal  = transpose(inverse(mat3(inModelMatrix)));
    outNormal     = mNormal * normalize(inNormal);
    outTangent    = mNormal * normalize(inTangent);
    outBitangent  = mNormal * normalize(inBitangent);
    outTextCoords = inTextCoords;
    vec4 worldPos  = inModelMatrix * vec4(inPos, 1);
    outPos        = worldPos;
    outMatIdx     = inMatIdx;
    gl_Position   = projUniform.matrix * viewUniform.matrix * worldPos;
}