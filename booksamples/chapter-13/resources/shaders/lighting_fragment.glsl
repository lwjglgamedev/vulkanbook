#version 450
// CREDITS: Most of the functions here have been obtained from this link: https://learnopengl.com/PBR
// developed by Joey de Vries, https://twitter.com/JoeyDeVriez, and licensed under the terms of the CC BY-NC 4.0,
// https://creativecommons.org/licenses/by-nc/4.0/legalcode

// TODO: Use an array of textures
layout (constant_id = 0) const int MAX_LIGHTS = 10;
layout (constant_id = 1) const int SHADOW_MAP_CASCADE_COUNT = 3;
const float PI = 3.14159265359;
const float ambient = 0.3;
const float SHADOW_FACTOR = 0.25;

// color cannot be vec3 due to std140 in GLSL
struct Light {
    vec4 position;
    vec4 color;
};

// splitDistance canot be float due to std140 in GLSL
struct CascadeShadow {
    mat4 projViewMatrix;
    vec4 splitDistance;
};

layout(location = 0) in vec2 inTextCoord;
layout(location = 0) out vec4 outFragColor;

layout(set = 0, binding = 0) uniform sampler2D albedoSampler;
layout(set = 0, binding = 1) uniform sampler2D normalsSampler;
layout(set = 0, binding = 2) uniform sampler2D pbrSampler;
layout(set = 0, binding = 3) uniform sampler2D depthSampler;
layout(set = 0, binding = 4) uniform sampler2D shadowSampler_0;
layout(set = 0, binding = 5) uniform sampler2D shadowSampler_1;
layout(set = 0, binding = 6) uniform sampler2D shadowSampler_2;

layout(set = 1, binding = 0) uniform UBO {
    vec4 ambientLightColor;
    uint count;
    Light lights[MAX_LIGHTS];
} lights;

layout(set = 2, binding = 0) uniform ProjUniform {
    mat4 invProjectionMatrix;
    mat4 invViewMatrix;
} projUniform;

layout(set = 3, binding = 0) uniform ShadowsUniforms {
    CascadeShadow cascadeshadows[SHADOW_MAP_CASCADE_COUNT];
} shadowsUniforms;

vec3 fresnelSchlick(float cosTheta, vec3 F0)
{
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

float DistributionGGX(vec3 N, vec3 H, float roughness)
{
    float a      = roughness*roughness;
    float a2     = a*a;
    float NdotH  = max(dot(N, H), 0.0);
    float NdotH2 = NdotH*NdotH;

    float num   = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return num / denom;
}

float GeometrySchlickGGX(float NdotV, float roughness)
{
    float r = (roughness + 1.0);
    float k = (r*r) / 8.0;

    float num   = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return num / denom;
}
float GeometrySmith(vec3 N, vec3 V, vec3 L, float roughness)
{
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2  = GeometrySchlickGGX(NdotV, roughness);
    float ggx1  = GeometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

vec3 calculateLight(Light light, vec3 lightDirection, vec3 position, vec3 normal, vec3 albedo,
float metallic, float roughness, float attenuation) {
    vec3 result;

    vec3 N = normalize(normal);
    vec3 L = normalize(lightDirection);
    vec3 V = normalize(-position);
    vec3 H = normalize(L + V);

    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    vec3 Lo = vec3(0.0);
    vec3 radiance = light.color.rgb * attenuation;

    // cook-torrance brdf
    float NDF = DistributionGGX(N, H, roughness);
    float G   = GeometrySmith(N, V, L, roughness);
    vec3 F    = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    vec3 numerator    = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0);
    vec3 specular     = numerator / max(denominator, 0.001);

    // add to outgoing radiance Lo
    float NdotL = max(dot(N, L), 0.0);
    Lo += (kD * albedo / PI + specular) * radiance * NdotL;

    return Lo;
}

vec3 calculateDirectionalLight(Light light, vec3 position, vec3 normal, vec3 color,
float metallic, float roughness) {
    float attenuation = 1.0;
    return calculateLight(light, light.position.xyz, position, normal, color, metallic, roughness,
    attenuation);
}

vec3 calculatePointLight(Light light, vec3 position, vec3 normal, vec3 color,
float metallic, float roughness) {
    vec3 lightDirection = light.position.xyz - position;
    float dist = length(lightDirection) * 0.2;
    float attenuation = 1.0 / (dist * dist);
    return calculateLight(light, lightDirection, position, normal, color, metallic, roughness,
    attenuation);
}

float calcShadow(vec4 P, vec2 inTextCoord, int idx)
{
    float shadow = 1.0;
    vec4 shadowCoord = P / P.w;
    shadowCoord.x = shadowCoord.x * 0.5 + 0.5;
    shadowCoord.y = (-shadowCoord.y) * 0.5 + 0.5;

    if (shadowCoord.z > -1.0 && shadowCoord.z < 1.0)
    {
        float dist;
        if (idx == 0)
        {
            dist = texture(shadowSampler_0, vec2(shadowCoord.xy)).r;
        }
        else if (idx == 1)
        {
            dist = texture(shadowSampler_1, vec2(shadowCoord.xy)).r;
        }
        else
        {
            dist = texture(shadowSampler_2, vec2(shadowCoord.xy)).r;
        }
        float bias = 0.0005;
        if (shadowCoord.w > 0.0 && dist < shadowCoord.z - bias)
        {
            shadow = SHADOW_FACTOR;
        }
    }
    return shadow;
}

void main() {
    vec3 albedo = texture(albedoSampler, inTextCoord).rgb;
    vec3 normal = normalize(2.0 * texture(normalsSampler, inTextCoord).rgb  - 1.0);
    vec3 pbrSampledValue = texture(pbrSampler, inTextCoord).rgb;
    float ao = 1.0;
    float roughness = pbrSampledValue.g;
    float metallic = pbrSampledValue.b;

    // Retrieve position from depth
    vec4 clip       = vec4(inTextCoord.x * 2.0 - 1.0, inTextCoord.y * -2.0 + 1.0, texture(depthSampler, inTextCoord).x, 1.0);
    vec4 view_w     = projUniform.invProjectionMatrix * clip;
    vec3 view_pos   = view_w.xyz / view_w.w;
    vec4 world_pos    = projUniform.invViewMatrix * vec4(view_pos, 1);

    int idx;
    for (int i=0; i<SHADOW_MAP_CASCADE_COUNT; i++)
    {
        if (abs(view_pos.z) < shadowsUniforms.cascadeshadows[i].splitDistance.x)
        {
            idx = i;
            break;
        }
    }
    float shadowFactor = calcShadow(shadowsUniforms.cascadeshadows[idx].projViewMatrix * world_pos, inTextCoord, idx);

    // Calculate lighting
    vec3 lightColor = vec3(0.0);
    vec3 ambientColor = vec3(0.5);
    for (uint i = 0U; i < lights.count; i++)
    {
        Light light = lights.lights[i];
        if (light.position.w == 0)
        {
            lightColor += calculateDirectionalLight(light, view_pos, normal, albedo, metallic, roughness);
        }
        else
        {
            lightColor += calculatePointLight(light, view_pos, normal, albedo, metallic, roughness);
        }
    }

    vec3 ambient = lights.ambientLightColor.rgb * albedo * ao;

    outFragColor = vec4(pow(ambient * shadowFactor + lightColor * shadowFactor, vec3(0.4545)), 1.0);
}