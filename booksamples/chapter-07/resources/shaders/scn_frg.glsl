#version 450

layout(location = 0) in vec2 inTextCoords;
layout(location = 0) out vec4 outFragColor;

void main()
{
    outFragColor = vec4(inTextCoords.x, inTextCoords.y, 0, 1);
}

