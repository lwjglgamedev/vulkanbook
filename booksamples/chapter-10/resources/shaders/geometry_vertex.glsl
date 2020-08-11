#version 450

layout(location = 0) in vec3 entityPos;
layout(location = 1) in vec3 entityNormal;
layout(location = 2) in vec2 entityTextCoords;

layout(location = 0) out vec3 outNormal;
layout(location = 1) out vec2 outTextCoords;

layout(set = 0, binding = 0) uniform ProjUniform {
    mat4 projectionMatrix;
} projUniform;
layout(set = 1, binding = 0) uniform ViewUniform {
    mat4 viewMatrix;
} viewUniform;

layout(push_constant) uniform matrices {
    mat4 modelMatrix;
} push_constants;

void main()
{
    gl_Position   = projUniform.projectionMatrix * viewUniform.viewMatrix * push_constants.modelMatrix * vec4(entityPos, 1);
    outNormal     = normalize(viewUniform.viewMatrix * vec4(entityNormal, 0)).xyz;
    outTextCoords = entityTextCoords;
}