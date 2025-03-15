#version 450
#extension GL_EXT_scalar_block_layout : require

// CREDITS: Most of the functions here have been obtained from this link: https://github.com/SaschaWillems/Vulkan
// developed by Sascha Willems, https://twitter.com/JoeyDeVriez, and licensed under the terms of the MIT License (MIT)

layout (constant_id = 0) const int SHADOW_MAP_CASCADE_COUNT = 3;
layout (constant_id = 1) const int USE_PCF = 0;
layout (constant_id = 2) const float BIAS = 0.0005;
layout (constant_id = 3) const int DEBUG_SHADOWS = 0;
const float PI = 3.14159265359;
const float SHADOW_FACTOR = 0.25;

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

float textureProj(vec4 shadowCoord, vec2 offset, uint cascadeIndex, float bias)
{
    float shadow = 1.0;

    if (shadowCoord.z > -1.0 && shadowCoord.z < 1.0) {
        float dist = texture(shadowSampler, vec3(shadowCoord.st + offset, cascadeIndex)).r;
        if (shadowCoord.w > 0 && dist < shadowCoord.z - bias) {
            shadow = SHADOW_FACTOR;
        }
    }
    return shadow;
}

float filterPCF(vec4 sc, uint cascadeIndex, float bias)
{
    ivec2 texDim = textureSize(shadowSampler, 0).xy;
    float scale = 0.75;
    float dx = scale * 1.0 / float(texDim.x);
    float dy = scale * 1.0 / float(texDim.y);

    float shadowFactor = 0.0;
    int count = 0;
    int range = 2;

    for (int x = -range; x <= range; x++) {
        for (int y = -range; y <= range; y++) {
            shadowFactor += textureProj(sc, vec2(dx*x, dy*y), cascadeIndex, bias);
            count++;
        }
    }
    return shadowFactor / count;
}

float calcShadow(vec4 worldPosition, uint cascadeIndex, float bias)
{
    vec4 shadowMapPosition = shadows.cascadeshadows[cascadeIndex].projViewMatrix * worldPosition;

    float shadow = 1.0;
    vec4 shadowCoord = shadowMapPosition / shadowMapPosition.w;
    shadowCoord.x = shadowCoord.x * 0.5 + 0.5;
    shadowCoord.y = (-shadowCoord.y) * 0.5 + 0.5;

    if (USE_PCF == 1) {
        shadow = filterPCF(shadowCoord, cascadeIndex, bias);
    } else {
        shadow = textureProj(shadowCoord, vec2(0, 0), cascadeIndex, bias);
    }
    return shadow;
}

float distributionGGX(float dotNH, float roughness)
{
    float alpha  = roughness * roughness;
    float alpha2 = alpha * alpha;
	float denom = dotNH * dotNH * (alpha2 - 1.0) + 1.0;
	return (alpha2)/(PI * denom*denom);
}

float geometrySchlickGGX(float dotNL, float dotNV, float roughness)
{
	float r = (roughness + 1.0);
	float k = (r*r) / 8.0;
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
    float metallic = pbr.b;

    vec3 N = normalize(normal);
    vec3 V = normalize(sceneInfo.camPos - worldPos);

    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    vec3 Lo = vec3(0.0);
    float bias = BIAS;
    for (uint i = 0U; i < sceneInfo.numLights; i++) {
        Light light = lights.lights[i];
        // calculate per-light radiance
        vec3 L;
        float attenuation;
        if (light.position.w == 0) {
            // Directional
            L = normalize(-light.position.xyz);
            attenuation = 1.0;
            bias = max(BIAS * 5 * (1.0 - dot(N, L)), BIAS);
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

    float shadowFactor = calcShadow(vec4(worldPos, 1), cascadeIndex, bias);

    vec3 ambient = sceneInfo.ambientLightColor.rgb * albedo;
    vec3 color = ambient + Lo;

    if ( shadowFactor < 0.3) {
        shadowFactor *= shadowFactor;
    }
    outFragColor = vec4(color * shadowFactor, 1.0);

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