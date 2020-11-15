#version 450

layout (constant_id = 0) const int SHADOW_MAP_CASCADE_COUNT = 3;

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec3 entityTangent;
layout(location = 3) in vec3 entityBitangent;
layout(location = 4) in vec2 entityTextCoords;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
    uint cascadeIndex;
} push_constants;

layout(set = 0, binding = 0) uniform ProjUniforms {
    mat4 projViewMatrices[SHADOW_MAP_CASCADE_COUNT];
} projUniforms;

void main()
{
    gl_Position = projUniforms.projViewMatrices[push_constants.cascadeIndex] * push_constants.modelMatrix * vec4(entityPos, 1.0f);
}