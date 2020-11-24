#version 450

layout(location = 0) in vec3 entityPos;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

void main()
{
    gl_Position = push_constants.modelMatrix * vec4(entityPos, 1.0f);
}