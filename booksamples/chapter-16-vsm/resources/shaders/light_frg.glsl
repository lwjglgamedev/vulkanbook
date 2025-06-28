#version 450
#extension GL_EXT_scalar_block_layout : require

// CREDITS: Most of the functions here have been obtained from this link: https://github.com/SaschaWillems/Vulkan
// developed by Sascha Willems, https://twitter.com/JoeyDeVriez, and licensed under the terms of the MIT License (MIT)

layout (constant_id = 0) const int SHADOW_MAP_CASCADE_COUNT = 3;
layout (constant_id = 1) const int DEBUG_SHADOWS = 0;
const float PI = 3.14159265359;
const int DEBUG_COLORS = 0;

struct Light {
    vec4 position;
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

float calculateShadow(vec4 worldPosition, uint cascadeIndex) {
    vec4 shadowMapPosition = shadows.cascadeshadows[cascadeIndex].projViewMatrix * worldPosition;
    vec2 uv = vec2(shadowMapPosition.x * 0.5 + 0.5, (-shadowMapPosition.y) * 0.5 + 0.5);
    float depth = shadowMapPosition.z;
    vec2 moments = texture(shadowSampler, vec3(uv, cascadeIndex)).rg;

    if (depth> 1.0) {
        return 1.0;
    }
    if (depth < 0.0) {
        return 0.0;
    }

    float shadowFactor = chebyshevUpperBound(moments, depth);
    return shadowFactor;
}

float calcVisibility(vec4 worldPosition, uint cascadeIndex) {
    vec4 shadowMapPosition = shadows.cascadeshadows[cascadeIndex].projViewMatrix * worldPosition;

    vec2 uv = vec2(shadowMapPosition.x * 0.5 + 0.5, (-shadowMapPosition.y) * 0.5 + 0.5);
    float depth = shadowMapPosition.z;
    vec2 moments = texture(shadowSampler, vec3(uv, cascadeIndex)).rg;

    float visibility = chebyshevUpperBound(moments, depth);
    return visibility;
}

float distributionGGX(float dotNH, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float denom = (dotNH * dotNH * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
    return a2 / max(denom, 0.0000001); // Prevent divide by zero
}

float geometrySchlickGGX(float dotNL, float dotNV, float roughness)
{
	float r = (roughness + 1.0);
	float k = (r * r) / 8.0;
	float GL = dotNL / (dotNL * (1.0 - k) + k);
	float GV = dotNV / (dotNV * (1.0 - k) + k);
	return GL * GV;
}

vec3 fresnelSchlick(vec3 albedo, float cosTheta, float metallic)
{
	vec3 F0 = mix(vec3(0.04), albedo, metallic);
	vec3 F = F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
	return F;
}

vec3 BRDF(vec3 albedo, vec3 lightColor, vec3 L, vec3 V, vec3 N, float metallic, float roughness)
{
	vec3 H = normalize (V + L);
	float dotNV = clamp(dot(N, V), 0.0, 1.0);
	float dotNL = clamp(dot(N, L), 0.0, 1.0);
	float dotLH = clamp(dot(L, H), 0.0, 1.0);
	float dotNH = clamp(dot(N, H), 0.0, 1.0);

	vec3 color = vec3(0.0);

	if (dotNL > 0.0 && dotNV > 0.0)
	{
		roughness = max(0.05, roughness);
		float D   = distributionGGX(dotNH, roughness);
		float G   = geometrySchlickGGX(dotNL, dotNV, roughness);
		vec3 F    = fresnelSchlick(albedo, dotNV, metallic);

		vec3 spec = D * F * G / (4.0 * dotNL * dotNV);

		color += spec * dotNL * lightColor;
	}

	return color;
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

    vec3 Lo = vec3(0.0);
    float lambert = 0.0f;
    for (uint i = 0U; i < sceneInfo.numLights; i++) {
        Light light = lights.lights[i];
        // calculate per-light radiance
        vec3 L;
        float attenuation;
        if (light.position.w == 0) {
            // Directional
            L = normalize(-light.position.xyz);
            attenuation = 1.0;
            lambert = max(0.0f, dot(N, L));
        } else {
            vec3 tmpSub = light.position.xyz - worldPos;
            L = normalize(tmpSub);
            float distance = length(tmpSub);
            attenuation = 1.0 / (distance * distance);
        }
        Lo += BRDF(albedo, light.color.rgb * attenuation, L, V, N, metallic, roughness);
    }

    uint cascadeIndex = 0;
    vec4 viewPos = sceneInfo.viewMatrix * worldPosW;
    for (uint i = 0; i < SHADOW_MAP_CASCADE_COUNT - 1; ++i) {
        if (viewPos.z < shadows.cascadeshadows[i].splitDistance.x) {
            cascadeIndex = i + 1;
        }
    }

    float shadow = calcVisibility(vec4(worldPos, 1), cascadeIndex);
    vec3 ambient = sceneInfo.ambientLightColor.rgb * albedo;
    vec3 color;
    if (DEBUG_COLORS == 1) {
        color = vec3(0.7) * lambert + sceneInfo.ambientLightColor.rgb * 0.3f;
        outFragColor = vec4(color * shadow, 1.0);
    } else {
        color = Lo * shadow;
        if (shadow < 0.3f)  {
            color += ambient * 0.1f;
        } else {
            color += ambient;
        }
        outFragColor = vec4(color, 1.0);
    }
    //outFragColor = vec4(Lo, 1.0);
    //outFragColor = vec4(vec3(shadow), 1.0);

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
            default :
            outFragColor.rgb *= vec3(1.0f, 1.0f, 0.25f);
            break;
        }
    }
}