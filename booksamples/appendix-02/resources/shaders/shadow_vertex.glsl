#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec3 entityTangent;
layout(location = 3) in vec3 entityBitangent;
layout(location = 4) in vec2 entityTextCoords;

// Instanced attributes
layout (location = 5) in mat4 entityModelMatrix;
layout (location = 9) in uint entityMatIdx;

layout (location = 0) out vec2 outTextCoord;
layout (location = 1) out flat uint outMatIdx;

void main()
{
    gl_Position = entityModelMatrix * vec4(entityPos, 1.0f);
    outTextCoord = entityTextCoords;
    outMatIdx = entityMatIdx;
}