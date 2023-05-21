#version 450

layout (set = 1, binding = 0) uniform sampler2D textSampler;
layout (location = 0) in vec2 inTextCoords;

void main()
{
    float alpha = texture(textSampler, inTextCoords).a;
    if (alpha < 0.5) {
        discard;
    }
}