#version 450

// You should change this manually if GraphConstants.SHADOW_MAP_CASCADE_COUNT changes
#define SHADOW_MAP_CASCADE_COUNT 3

layout (triangles, invocations = SHADOW_MAP_CASCADE_COUNT) in;
layout (triangle_strip, max_vertices = 3) out;

layout (location = 0) in vec2 inTextCoords[];
layout (location = 1) in flat uint inMaterialIdx[];

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out flat uint outMaterialIdx;

layout(set = 0, binding = 0) uniform ProjUniforms {
    mat4 projViewMatrices[SHADOW_MAP_CASCADE_COUNT];
} projUniforms;


void main()
{
    for (int i = 0; i < 3; i++)
    {
        outTextCoords  = inTextCoords[i];
        outMaterialIdx = inMaterialIdx[i];
        gl_Layer = gl_InvocationID;
        gl_Position = projUniforms.projViewMatrices[gl_InvocationID] * gl_in[i].gl_Position;
        EmitVertex();
    }
    EndPrimitive();
}