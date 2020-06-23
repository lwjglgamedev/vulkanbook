#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec2 entityTextCoords;

layout(location = 0) out vec2 textCoords;

layout(push_constant) uniform matrix {
    mat4 model_matrix;
} model;

void main()
{
    gl_Position = model.model_matrix * vec4(entityPos, 1);
    textCoords = entityTextCoords;
}

