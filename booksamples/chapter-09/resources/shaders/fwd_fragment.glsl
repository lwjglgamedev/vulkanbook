#version 450

layout(location = 0) in vec2 textCoords;
layout(location = 0) out vec4 uFragColor;

layout(set = 2, binding = 0) uniform sampler2D textSampler;

void main()
{
    uFragColor = texture(textSampler, textCoords);
}

