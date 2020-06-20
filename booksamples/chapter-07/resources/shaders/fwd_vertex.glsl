#version 450

layout(location = 0) in vec3 entityPos;

void main()
{
    gl_Position = vec4(entityPos, 1);
}

