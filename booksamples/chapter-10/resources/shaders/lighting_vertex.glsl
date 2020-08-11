#version 450
precision highp float;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec2 inTextCoord;

layout(location = 0) out vec2 outTextCoord;

void main()
{
    gl_Position =  vec4(inPosition, 1);
    outTextCoord = inTextCoord;
}