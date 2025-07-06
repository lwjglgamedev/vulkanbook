#version 450

layout(location = 0) in vec3 inPos;
layout(location = 1) in vec2 intTextCoords;

layout(location = 0) out vec2 outTextCoords;

layout(push_constant) uniform matrices {
    mat4 projectionMatrix;
    mat4 modelMatrix;
} push_constants;

void main()
{
    gl_Position = push_constants.projectionMatrix * push_constants.modelMatrix * vec4(inPos, 1);
    outTextCoords = intTextCoords;
}

