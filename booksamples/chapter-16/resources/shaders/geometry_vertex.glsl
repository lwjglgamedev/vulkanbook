#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec3 entityTangent;
layout(location = 3) in vec3 entityBitangent;
layout(location = 4) in vec2 entityTextCoords;

// Instanced attributes
layout (location = 5) in mat4 entityModelMatrix;
layout (location = 9) in uint entityMatIdx;

layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec3 outTangent;
layout(location = 2) out vec3 outBitangent;
layout(location = 3) out vec2 outTextCoords;
layout(location = 4) flat out uint outMatIdx;

out gl_PerVertex
{
    vec4 gl_Position;
};

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 projectionMatrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 viewMatrix;
} viewUniform;

void main()
{
    mat4 modelViewMatrix = viewUniform.viewMatrix * entityModelMatrix;
    outNormal     = normalize(modelViewMatrix * vec4(entityNormal, 0)).xyz;
    outTangent    = normalize(modelViewMatrix * vec4(entityTangent, 0)).xyz;
    outBitangent  = normalize(modelViewMatrix * vec4(entityBitangent, 0)).xyz;
    outTextCoords = entityTextCoords;
    outMatIdx     = entityMatIdx;
    gl_Position   = projUniform.projectionMatrix * modelViewMatrix * vec4(entityPos, 1);
}