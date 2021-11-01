#version 450
// CREDITS: Most of the functions here have been obtained from this link: https://learnopengl.com/PBR
// developed by Joey de Vries, https://twitter.com/JoeyDeVriez, and licensed under the terms of the CC BY-NC 4.0,
// https://creativecommons.org/licenses/by-nc/4.0/legalcode

layout (constant_id = 0) const int MAX_LIGHTS = 10;
const float PI = 3.14159265359;

// color cannot be vec3 due to std140 in GLSL
struct Light {
    vec4 position;
    vec4 color;
};

layout(location = 0) in vec2 inTextCoord;

layout(location = 0) out vec4 outFragColor;

layout(set = 0, binding = 0) uniform sampler2D albedoSampler;
layout(set = 0, binding = 1) uniform sampler2D normalsSampler;
layout(set = 0, binding = 2) uniform sampler2D pbrSampler;
layout(set = 0, binding = 3) uniform sampler2D depthSampler;
layout(set = 1, binding = 0) uniform UBO {
    vec4 ambientLightColor;
    uint count;
    Light lights[MAX_LIGHTS];
} lights;
layout(set = 2, binding = 0) uniform ProjUniform {
    mat4 invProjectionMatrix;
} projUniform;

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

void main() {
    vec3 albedo = texture(albedoSampler, inTextCoord).rgb;
    vec3 normal = normalize(2.0 * texture(normalsSampler, inTextCoord).rgb  - 1.0);
    vec3 pbrSampledValue = texture(pbrSampler, inTextCoord).rgb;
    float ao = pbrSampledValue.r;
    float roughness = pbrSampledValue.g;
    float metallic = pbrSampledValue.b;

    // Retrieve position from depth
    vec4 clip    = vec4(inTextCoord.x * 2.0 - 1.0, inTextCoord.y * -2.0 + 1.0, texture(depthSampler, inTextCoord).x, 1.0);
    vec4 world_w = projUniform.invProjectionMatrix * clip;
    vec3 pos     = world_w.xyz / world_w.w;

    // Calculate lighting
    vec3 lightColor = vec3(0.0);
    vec3 ambientColor = vec3(0.5);
    for (uint i = 0U; i < lights.count; i++)
    {
        Light light = lights.lights[i];
        if (light.position.w == 0)
        {
            lightColor += calculateDirectionalLight(light, pos, normal, albedo, metallic, roughness);
        }
        else
        {
            lightColor += calculatePointLight(light, pos, normal, albedo, metallic, roughness);
        }
    }

    vec3 ambient = lights.ambientLightColor.rgb * albedo * ao;

    outFragColor = vec4(ambient + lightColor, 1.0);
}