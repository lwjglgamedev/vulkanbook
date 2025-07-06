#version 450
#extension GL_EXT_buffer_reference: require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : enable

layout(std430, buffer_reference) buffer FloatBuf {
    float data[];
};

layout(std430, buffer_reference) buffer MatricesBuf {
    mat4[] data;
};

layout (local_size_x=32, local_size_y=1, local_size_z=1) in;

layout(push_constant) uniform Pc {
    FloatBuf srcBuf;
    FloatBuf weightsBuf;
    MatricesBuf jointsBuf;
    FloatBuf dstBuf;
    uint64_t srcBuffFloatSize;
} pc;


void main()
{
    int baseIdxSrcBuf = int(gl_GlobalInvocationID.x) * 14;
    if ( baseIdxSrcBuf >= pc.srcBuffFloatSize) {
        return;
    }
    int baseIdxWeightsBuf  = int(gl_GlobalInvocationID.x) * 8;
    vec4 weights = vec4(pc.weightsBuf.data[baseIdxWeightsBuf], pc.weightsBuf.data[baseIdxWeightsBuf + 1], pc.weightsBuf.data[baseIdxWeightsBuf + 2], pc.weightsBuf.data[baseIdxWeightsBuf + 3]);
    ivec4 joints = ivec4(pc.weightsBuf.data[baseIdxWeightsBuf + 4], pc.weightsBuf.data[baseIdxWeightsBuf + 5], pc.weightsBuf.data[baseIdxWeightsBuf + 6], pc.weightsBuf.data[baseIdxWeightsBuf + 7]);

    vec4 position = vec4(pc.srcBuf.data[baseIdxSrcBuf], pc.srcBuf.data[baseIdxSrcBuf + 1], pc.srcBuf.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * pc.jointsBuf.data[joints.x] * position +
    weights.y * pc.jointsBuf.data[joints.y] * position +
    weights.z * pc.jointsBuf.data[joints.z] * position +
    weights.w * pc.jointsBuf.data[joints.w] * position;
    pc.dstBuf.data[baseIdxSrcBuf] = position.x / position.w;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = position.y / position.w;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = position.z / position.w;

    mat3 matJoint1 = mat3(transpose(inverse(pc.jointsBuf.data[joints.x])));
    mat3 matJoint2 = mat3(transpose(inverse(pc.jointsBuf.data[joints.y])));
    mat3 matJoint3 = mat3(transpose(inverse(pc.jointsBuf.data[joints.z])));
    baseIdxSrcBuf += 3;
    vec3 normal = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    normal =
    weights.x * matJoint1 * normal +
    weights.y * matJoint2 * normal +
    weights.z * matJoint3 * normal;
    normal = normalize(normal);
    pc.dstBuf.data[baseIdxSrcBuf] = normal.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = normal.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = normal.z;

    baseIdxSrcBuf += 3;
    vec3 tangent = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    tangent =
    weights.x * matJoint1 * tangent +
    weights.y * matJoint2 * tangent +
    weights.z * matJoint3 * tangent;
    tangent = normalize(tangent);
    pc.dstBuf.data[baseIdxSrcBuf] = tangent.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = tangent.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = tangent.z;

    baseIdxSrcBuf += 3;
    vec3 bitangent = vec3(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1], pc.srcBuf .data[baseIdxSrcBuf + 2]);
    bitangent =
    weights.x * matJoint1 * bitangent +
    weights.y * matJoint2 * bitangent +
    weights.z * matJoint3 * bitangent;
    bitangent = normalize(bitangent);
    pc.dstBuf.data[baseIdxSrcBuf] = bitangent.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = bitangent.y;
    pc.dstBuf.data[baseIdxSrcBuf + 2] = bitangent.z;

    baseIdxSrcBuf += 3;
    vec2 textCoords = vec2(pc.srcBuf .data[baseIdxSrcBuf], pc.srcBuf .data[baseIdxSrcBuf + 1]);
    pc.dstBuf.data[baseIdxSrcBuf] = textCoords.x;
    pc.dstBuf.data[baseIdxSrcBuf + 1] = textCoords.y;
}