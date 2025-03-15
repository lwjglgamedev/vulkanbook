#version 460
#extension GL_EXT_ray_tracing : require

const int NBSAMPLES = 10;

struct hitPayload
{
  vec3 hitValue;
  uint seed;
  vec3 barycentrics;
  vec2 textCoord;
};

layout(set=0, binding = 0) uniform ProjUniform {
    mat4 matrix;
} projUniform;
layout(set=1, binding = 0) uniform ViewUniform {
    mat4 matrix;
} viewUniform;
layout(set=2, binding=0, rgba8) uniform image2D outImage;
layout(set=3, binding=0) uniform accelerationStructureEXT topLevelAS;

layout(location = 0) rayPayloadEXT hitPayload prd;
layout(push_constant) uniform pc {
    int frame;
} push_constants;

// Generate a random unsigned int from two unsigned int values, using 16 pairs
// of rounds of the Tiny Encryption Algorithm. See Zafar, Olano, and Curtis,
// "GPU Random Numbers via the Tiny Encryption Algorithm"
uint tea(uint val0, uint val1)
{
  uint v0 = val0;
  uint v1 = val1;
  uint s0 = 0;

  for(uint n = 0; n < 16; n++)
  {
    s0 += 0x9e3779b9;
    v0 += ((v1 << 4) + 0xa341316c) ^ (v1 + s0) ^ ((v1 >> 5) + 0xc8013ea4);
    v1 += ((v0 << 4) + 0xad90777d) ^ (v0 + s0) ^ ((v0 >> 5) + 0x7e95761e);
  }

  return v0;
}

// Generate a random unsigned int in [0, 2^24) given the previous RNG state
// using the Numerical Recipes linear congruential generator
uint lcg(inout uint prev)
{
  uint LCG_A = 1664525u;
  uint LCG_C = 1013904223u;
  prev       = (LCG_A * prev + LCG_C);
  return prev & 0x00FFFFFF;
}

// Generate a random float in [0, 1) given the previous RNG state
float rnd(inout uint prev)
{
  return (float(lcg(prev)) / float(0x01000000));
}

void main() {
   // Initialize the random number
   uint seed = tea(gl_LaunchIDEXT.y * gl_LaunchSizeEXT.x + gl_LaunchIDEXT.x, push_constants.frame);

   uint rayFlags = gl_RayFlagsNoneEXT;
   uint cullMask = 0xFFu;
   uint sbtRecordOffset = 0;
   uint sbtRecordStride = 0;
   uint missIndex = 0;
   const int payload = 0;

   mat4 invView = inverse(viewUniform.matrix);
   vec4 rayOrigin = invView * vec4(0,0,0,1);

   float rayMin = 0.001;
   float rayMax = 10000.0;
   vec3 hitValues = vec3(0);

   for(int smpl = 0; smpl < NBSAMPLES; smpl++) {
       float r1 = rnd(seed);
       float r2 = rnd(seed);
       vec2 subpixel_jitter = push_constants.frame == 0 ? vec2(0.5f, 0.5f) : vec2(r1, r2);

       const vec2 pixelCenter = vec2(gl_LaunchIDEXT.xy) + subpixel_jitter;
       const vec2 inUV = pixelCenter/vec2(gl_LaunchSizeEXT.xy);
       vec2 d = inUV * 2.0 - 1.0;

       vec4 target = inverse(projUniform.matrix) * vec4(d.x, d.y, 1, 1) ;
       vec4 rayDirection = invView * vec4(normalize(target.xyz), 0) ;

       prd.seed = tea(gl_LaunchIDEXT.y * gl_LaunchSizeEXT.x + gl_LaunchIDEXT.x, push_constants.frame);

       traceRayEXT(topLevelAS, rayFlags, cullMask, sbtRecordOffset, sbtRecordStride, missIndex, rayOrigin.xyz, rayMin,
           rayDirection.xyz, rayMax, payload);

       hitValues += prd.hitValue;
   }
   prd.hitValue = hitValues / NBSAMPLES;

   ivec2 pixelCoord = ivec2(gl_LaunchIDEXT.xy);

   if(push_constants.frame > 0) {
       // Accumulate over time
       float a = 1.0f / float(push_constants.frame + 1);
       vec3 old_color = imageLoad(outImage, pixelCoord).xyz;
       imageStore(outImage, pixelCoord, vec4(mix(old_color, prd.hitValue, a), 1.f));
   } else {
       // First frame, replace the value in the buffer
       imageStore(outImage, pixelCoord, vec4(prd.hitValue, 1.f));
   }
}