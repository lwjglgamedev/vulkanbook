#version 450

layout(location = 0) out vec2 outTextCoord;

void main()
{
    outTextCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(outTextCoord.x * 2.0f - 1.0f, outTextCoord.y * -2.0f + 1.0f, 0.0f, 1.0f);
}