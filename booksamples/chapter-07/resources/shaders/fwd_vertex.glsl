#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec2 entityTextCoords;

layout(location = 0) out vec2 textCoords;

layout(push_constant) uniform matrices {
    mat4 matrices[2];
} push_constants;

void main()
{
    gl_Position = push_constants.matrices[0] * push_constants.matrices[1] * vec4(entityPos, 1);
    textCoords = entityTextCoords;
}

