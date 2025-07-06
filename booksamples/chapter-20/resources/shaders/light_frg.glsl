#version 450
#extension GL_EXT_scalar_block_layout: require

// CREDITS: Most of the functions here have been obtained from this link: https://github.com/SaschaWillems/Vulkan
// developed by Sascha Willems, https://twitter.com/JoeyDeVriez, and licensed under the terms of the MIT License (MIT)

layout (constant_id = 0) const int SHADOW_MAP_CASCADE_COUNT = 3;
layout (constant_id = 1) const int DEBUG_SHADOWS = 0;
const float PI = 3.14159265359;

struct Light {
    vec3 position;
    uint directional;
    float intensity;
    vec3 color;
};
struct CascadeShadow {
    mat4 projViewMatrix;
    vec4 splitDistance;
};

layout(location = 0) in vec2 inTextCoord;

layout(location = 0) out vec4 outFragColor;

layout(set = 0, binding = 0) uniform sampler2D posSampler;
layout(set = 0, binding = 1) uniform sampler2D albedoSampler;
layout(set = 0, binding = 2) uniform sampler2D normalsSampler;
layout(set = 0, binding = 3) uniform sampler2D pbrSampler;
layout(set = 0, binding = 4) uniform sampler2DArray shadowSampler;

layout(scalar, set = 1, binding = 0) readonly buffer Lights {
    Light lights[];
} lights;
layout(set = 2, binding = 0) readonly buffer Shadows {
    CascadeShadow cascadeshadows[];
} shadows;
layout(scalar, set = 3, binding = 0) uniform SceneInfo {
    vec3 camPos;
    float ambientLightIntensity;
    vec3 ambientLightColor;
    uint numLights;
    mat4 viewMatrix;
} sceneInfo;

float chebyshevUpperBound(vec2 moments, float t) {
    // Surface is fully lit if the current fragment is before the light occluder
    if (t <= moments.x)
    return 1.0;

    // Compute variance
    float variance = moments.y - (moments.x * moments.x);
    variance = max(variance, 0.00002); // Small epsilon to avoid divide by zero

    // Compute probabilistic upper bound
    float d = t - moments.x;
    float p_max = variance / (variance + d * d);

    // Reduce light bleeding
    p_max = smoothstep(0.2, 1.0, p_max);

    return p_max;
}

float calcVisibility(vec4 worldPosition, uint cascadeIndex) {
    vec4 shadowMapPosition = shadows.cascadeshadows[cascadeIndex].projViewMatrix * worldPosition;

    vec2 uv = vec2(shadowMapPosition.x * 0.5 + 0.5, (-shadowMapPosition.y) * 0.5 + 0.5);
    float depth = shadowMapPosition.z;
    vec2 moments = texture(shadowSampler, vec3(uv, cascadeIndex)).rg;

    float visibility = chebyshevUpperBound(moments, depth);
    return visibility;
}

float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;

    float nom = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;

    return nom / denom;
}

float geometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;

    float nom = NdotV;
    float denom = NdotV * (1.0 - k) + k;

    return nom / denom;
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = geometrySchlickGGX(NdotV, roughness);
    float ggx1 = geometrySchlickGGX(NdotL, roughness);

    return ggx1 * ggx2;
}

vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

vec3 calculatePointLight(Light light, vec3 worldPos, vec3 V, vec3 N, vec3 F0, vec3 albedo, float metallic, float roughness) {
    vec3 tmpSub = light.position - worldPos;
    vec3 L = normalize(tmpSub - worldPos);
    vec3 H = normalize(V + L);

    // Calculate distance and attenuation
    float distance = length(tmpSub);
    float attenuation = 1.0 / (distance * distance);
    float intensity = 10.0f;
    vec3 radiance = light.color * light.intensity * attenuation;

    // Cook-Torrance BRDF
    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 numerator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
    vec3 specular = numerator / denominator;

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    float NdotL = max(dot(N, L), 0.0);
    return (kD * albedo / PI + specular) * radiance * NdotL;
}

vec3 calculateDirectionalLight(Light light, vec3 V, vec3 N, vec3 F0, vec3 albedo, float metallic, float roughness) {
    vec3 L = normalize(-light.position);
    vec3 H = normalize(V + L);

    vec3 radiance = light.color * light.intensity;

    // Cook-Torrance BRDF
    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 numerator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
    vec3 specular = numerator / denominator;

    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= 1.0 - metallic;

    float NdotL = max(dot(N, L), 0.0);
    return (kD * albedo / PI + specular) * radiance * NdotL;
}

void main() {
    vec3 albedo    = texture(albedoSampler, inTextCoord).rgb;
    vec3 normal    = texture(normalsSampler, inTextCoord).rgb;
    vec4 worldPosW = texture(posSampler, inTextCoord);
    vec3 worldPos  = worldPosW.xyz;
    vec3 pbr       = texture(pbrSampler, inTextCoord).rgb;

    float roughness = pbr.g;
    float metallic  = pbr.b;

    vec3 N = normalize(normal);
    vec3 V = normalize(sceneInfo.camPos - worldPos);

    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    uint cascadeIndex = 0;
    vec4 viewPos = sceneInfo.viewMatrix * worldPosW;
    for (uint i = 0; i < SHADOW_MAP_CASCADE_COUNT - 1; ++i) {
        if (viewPos.z < shadows.cascadeshadows[i].splitDistance.x) {
            cascadeIndex = i + 1;
        }
    }
    float shadow = calcVisibility(vec4(worldPos, 1), cascadeIndex);

    vec3 Lo = vec3(0.0);
    for (uint i = 0; i < sceneInfo.numLights; i++) {
        Light light = lights.lights[i];
        if (light.directional == 1) {
            Lo += calculateDirectionalLight(light, V, N, F0, albedo, metallic, roughness);
        } else {
            Lo += calculatePointLight(light, worldPos, V, N, F0, albedo, metallic, roughness);
        }
    }
    vec3 ambient = sceneInfo.ambientLightColor * albedo * sceneInfo.ambientLightIntensity;
    outFragColor = vec4(Lo * shadow + ambient, 1.0f);

    if (DEBUG_SHADOWS == 1) {
        switch (cascadeIndex) {
            case 0:
                outFragColor.rgb *= vec3(1.0f, 0.25f, 0.25f);
                break;
            case 1:
                outFragColor.rgb *= vec3(0.25f, 1.0f, 0.25f);
                break;
            case 2:
                outFragColor.rgb *= vec3(0.25f, 0.25f, 1.0f);
                break;
            default:
                outFragColor.rgb *= vec3(1.0f, 1.0f, 0.25f);
                break;
        }
    }
}