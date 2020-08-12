#version 450
precision highp float;

layout(location = 0) out vec2 outTextCoord;

void main()
{
    outTextCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(outTextCoord * 2.0f - 1.0f, 0.0f, 1.0f);
}