#version 450

layout (constant_id = 0) const int USE_FXAA = 0;

const float GAMMA = 2.2;
const float SPAN_MAX = 8.0;
const float REDUCE_MIN = 1.0/128.0;
const float REDUCE_MUL = 1.0/32.0;

layout (location = 0) in vec2 inTextCoord;
layout (location = 0) out vec4 outFragColor;

layout (set = 0, binding = 0) uniform sampler2D inputTexture;
layout (set = 1, binding = 0) uniform ScreenSize {
    vec2 size;
} screenSize;

vec4 gamma(vec4 color) {
    return color = vec4(pow(color.rgb, vec3(1.0 / GAMMA)), color.a);
}

// Credit: https://mini.gmshaders.com/p/gm-shaders-mini-fxaa
vec4 fxaa(sampler2D tex, vec2 uv) {
    vec2 u_texel = 1.0 / screenSize.size;

	//Sample center and 4 corners
    vec3 rgbCC = texture(tex, uv).rgb;
    vec3 rgb00 = texture(tex, uv+vec2(-0.5,-0.5)*u_texel).rgb;
    vec3 rgb10 = texture(tex, uv+vec2(+0.5,-0.5)*u_texel).rgb;
    vec3 rgb01 = texture(tex, uv+vec2(-0.5,+0.5)*u_texel).rgb;
    vec3 rgb11 = texture(tex, uv+vec2(+0.5,+0.5)*u_texel).rgb;

	//Luma coefficients
    const vec3 luma = vec3(0.299, 0.587, 0.114);
	//Get luma from the 5 samples
    float lumaCC = dot(rgbCC, luma);
    float luma00 = dot(rgb00, luma);
    float luma10 = dot(rgb10, luma);
    float luma01 = dot(rgb01, luma);
    float luma11 = dot(rgb11, luma);

	//Compute gradient from luma values
    vec2 dir = vec2((luma01 + luma11) - (luma00 + luma10), (luma00 + luma01) - (luma10 + luma11));
	//Diminish dir length based on total luma
    float dirReduce = max((luma00 + luma10 + luma01 + luma11) * REDUCE_MUL, REDUCE_MIN);
	//Divide dir by the distance to nearest edge plus dirReduce
    float rcpDir = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
	//Multiply by reciprocal and limit to pixel span
    dir = clamp(dir * rcpDir, -SPAN_MAX, SPAN_MAX) * u_texel.xy;

	//Average middle texels along dir line
    vec4 A = 0.5 * (
        texture(tex, uv - dir * (1.0/6.0)) +
        texture(tex, uv + dir * (1.0/6.0)));

	//Average with outer texels along dir line
    vec4 B = A * 0.5 + 0.25 * (
        texture(tex, uv - dir * (0.5)) +
        texture(tex, uv + dir * (0.5)));


	//Get lowest and highest luma values
    float lumaMin = min(lumaCC, min(min(luma00, luma10), min(luma01, luma11)));
    float lumaMax = max(lumaCC, max(max(luma00, luma10), max(luma01, luma11)));

	//Get average luma
	float lumaB = dot(B.rgb, luma);
	//If the average is outside the luma range, using the middle average
    return ((lumaB < lumaMin) || (lumaB > lumaMax)) ? A : B;
}

void main() {
    if (USE_FXAA == 0) {
        outFragColor = gamma(texture(inputTexture, inTextCoord));
        return;
    }

    outFragColor = fxaa(inputTexture, inTextCoord);
    outFragColor = gamma(outFragColor);
}