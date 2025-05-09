#version 450
#extension GL_EXT_scalar_block_layout : require

// CREDITS: Most of the functions here have been obtained from this link: https://github.com/SaschaWillems/Vulkan
// developed by Sascha Willems, https://twitter.com/JoeyDeVriez, and licensed under the terms of the MIT License (MIT)

const int MAX_LIGHTS = 10;
const float PI = 3.14159265359;

struct Light {
    vec4 position;
    vec3 color;
};

layout(location = 0) in vec2 inTextCoord;

layout(location = 0) out vec4 outFragColor;

layout(set = 0, binding = 0) uniform sampler2D posSampler;
layout(set = 0, binding = 1) uniform sampler2D albedoSampler;
layout(set = 0, binding = 2) uniform sampler2D normalsSampler;
layout(set = 0, binding = 3) uniform sampler2D pbrSampler;

layout(scalar, set = 1, binding = 0) readonly buffer Lights {
    Light lights[];
} lights;
layout(scalar, set = 2, binding = 0) uniform SceneInfo {
    vec3 camPos;
    vec3 ambientLightColor;
    uint numLights;
} sceneInfo;

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
    vec3 albedo   = texture(albedoSampler, inTextCoord).rgb;
    vec3 normal   = texture(normalsSampler, inTextCoord).rgb;
    vec3 worldPos = texture(posSampler, inTextCoord).rgb;
    vec3 pbr      = texture(pbrSampler, inTextCoord).rgb;

    float roughness = pbr.g;
    float metallic  = pbr.b;

    vec3 N = normalize(normal);
    vec3 V = normalize(sceneInfo.camPos - worldPos);

    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    vec3 Lo = vec3(0.0);
    for (uint i = 0U; i < sceneInfo.numLights; i++) {
        Light light = lights.lights[i];
        // calculate per-light radiance
        vec3 L;
        float attenuation;
        if (light.position.w == 0) {
            // Directional
            L = normalize(-light.position.xyz);
            attenuation = 1.0;
        } else {
            vec3 tmpSub = light.position.xyz - worldPos;
            L = normalize(tmpSub);
            float distance = length(tmpSub);
            attenuation = 1.0 / (distance * distance);
        }
        Lo += BRDF(albedo, light.color.rgb * attenuation, L, V, N, metallic, roughness);
    }

    vec3 ambient = sceneInfo.ambientLightColor.rgb * albedo;
    vec3 color = ambient + Lo;

    outFragColor = vec4(color, 1.0);
}