#version 450

layout(location = 0) in vec2 textCoords;
layout(location = 0) out vec4 uFragColor;

void main()
{
    uFragColor = vec4(textCoords.x, textCoords.y, 0, 1);
}

