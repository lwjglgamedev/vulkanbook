#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec3 entityTangent;
layout(location = 3) in vec3 entityBitangent;
layout(location = 4) in vec2 entityTextCoords;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

layout (location = 0) out vec2 outTextCoord;

void main()
{
    gl_Position = push_constants.modelMatrix * vec4(entityPos, 1.0f);
    outTextCoord = entityTextCoords;
}