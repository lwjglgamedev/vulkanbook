#version 450

layout (std430, set=0, binding=0) readonly buffer srcBuf {
    float data[];
} srcVector;

layout (std430, set=1, binding=0) readonly buffer weightsBuf {
    float data[];
} weightsVector;

layout (std430, set=2, binding=0) buffer dstBuf {
    float data[];
} dstVector;

layout (std430, set=3, binding=0) readonly buffer jointBuf {
    mat4 data[];
} jointMatrices;

layout (local_size_x=32, local_size_y=1, local_size_z=1) in;

layout(push_constant) uniform pushConstants {
    uint srcOffset;
    uint srcSize;
    uint weightsOffset;
    uint jointMatricesOffset;
    uint dstOffset;
} push_constants;

void main()
{
    int baseIdx = int(gl_GlobalInvocationID.x) * 14;
    uint baseIdxWeightsBuf  = push_constants.weightsOffset + int(gl_GlobalInvocationID.x) * 8;
    uint baseIdxSrcBuf = push_constants.srcOffset + baseIdx;
    uint baseIdxDstBuf = push_constants.dstOffset + baseIdx;
    if (baseIdx >= push_constants.srcSize) {
        return;
    }

    vec4 weights = vec4(weightsVector.data[baseIdxWeightsBuf], weightsVector.data[baseIdxWeightsBuf + 1], weightsVector.data[baseIdxWeightsBuf + 2], weightsVector.data[baseIdxWeightsBuf + 3]);
    ivec4 joints = ivec4(weightsVector.data[baseIdxWeightsBuf + 4], weightsVector.data[baseIdxWeightsBuf + 5], weightsVector.data[baseIdxWeightsBuf + 6], weightsVector.data[baseIdxWeightsBuf + 7]);

    vec4 position = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * jointMatrices.data[push_constants.jointMatricesOffset + joints.x] * position +
    weights.y * jointMatrices.data[push_constants.jointMatricesOffset + joints.y] * position +
    weights.z * jointMatrices.data[push_constants.jointMatricesOffset + joints.z] * position +
    weights.w * jointMatrices.data[push_constants.jointMatricesOffset + joints.w] * position;
    dstVector.data[baseIdxDstBuf] = position.x / position.w;
    dstVector.data[baseIdxDstBuf + 1] = position.y / position.w;
    dstVector.data[baseIdxDstBuf + 2] = position.z / position.w;

    mat3 matJoint1 = mat3(transpose(inverse(jointMatrices.data[push_constants.jointMatricesOffset + joints.x])));
    mat3 matJoint2 = mat3(transpose(inverse(jointMatrices.data[push_constants.jointMatricesOffset + joints.y])));
    mat3 matJoint3 = mat3(transpose(inverse(jointMatrices.data[push_constants.jointMatricesOffset + joints.z])));

    baseIdxSrcBuf += 3;
    baseIdxDstBuf += 3;
    vec3 normal = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    normal =
    weights.x * matJoint1 * normal +
    weights.y * matJoint2 * normal +
    weights.z * matJoint3 * normal;
    normal = normalize(normal);
    dstVector.data[baseIdxDstBuf] = normal.x;
    dstVector.data[baseIdxDstBuf + 1] = normal.y;
    dstVector.data[baseIdxDstBuf + 2] = normal.z;

    baseIdxSrcBuf += 3;
    baseIdxDstBuf += 3;
    vec3 tangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    tangent =
    weights.x * matJoint1 * tangent +
    weights.y * matJoint2 * tangent +
    weights.z * matJoint3 * tangent;
    tangent = normalize(tangent);
    dstVector.data[baseIdxDstBuf] = tangent.x;
    dstVector.data[baseIdxDstBuf + 1] = tangent.y;
    dstVector.data[baseIdxDstBuf + 2] = tangent.z;

    baseIdxSrcBuf += 3;
    baseIdxDstBuf += 3;
    vec3 bitangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    bitangent =
    weights.x * matJoint1 * bitangent +
    weights.y * matJoint2 * bitangent +
    weights.z * matJoint3 * bitangent;
    bitangent = normalize(bitangent);
    dstVector.data[baseIdxDstBuf] = bitangent.x;
    dstVector.data[baseIdxDstBuf + 1] = bitangent.y;
    dstVector.data[baseIdxDstBuf + 2] = bitangent.z;

    baseIdxSrcBuf += 3;
    baseIdxDstBuf += 3;
    vec2 textCoords = vec2(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1]);
    dstVector.data[baseIdxDstBuf] = textCoords.x;
    dstVector.data[baseIdxDstBuf + 1] = textCoords.y;
}