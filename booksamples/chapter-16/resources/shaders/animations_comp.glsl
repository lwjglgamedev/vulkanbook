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

// TODO: Move this to storage buffer
layout (std430, set=3, binding=0) readonly buffer jointBuf {
    mat4 data[];
} jointMatrices;

layout (local_size_x=32, local_size_y=1, local_size_z=1) in;

void main()
{
    int baseIdxWeightsBuf  = int(gl_GlobalInvocationID.x) * 8;
    vec4 weights = vec4(weightsVector.data[baseIdxWeightsBuf], weightsVector.data[baseIdxWeightsBuf + 1], weightsVector.data[baseIdxWeightsBuf + 2], weightsVector.data[baseIdxWeightsBuf + 3]);
    ivec4 joints = ivec4(weightsVector.data[baseIdxWeightsBuf + 4], weightsVector.data[baseIdxWeightsBuf + 5], weightsVector.data[baseIdxWeightsBuf + 6], weightsVector.data[baseIdxWeightsBuf + 7]);

    int baseIdxSrcBuf = int(gl_GlobalInvocationID.x) * 14;
    vec4 position = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 1);
    position =
    weights.x * jointMatrices.data[joints.x] * position +
    weights.y * jointMatrices.data[joints.y] * position +
    weights.z * jointMatrices.data[joints.z] * position +
    weights.w * jointMatrices.data[joints.w] * position;
    dstVector.data[baseIdxSrcBuf] = position.x / position.w;
    dstVector.data[baseIdxSrcBuf + 1] = position.y / position.w;
    dstVector.data[baseIdxSrcBuf + 2] = position.z / position.w;

    baseIdxSrcBuf += 3;
    vec4 normal = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    normal =
    weights.x * jointMatrices.data[joints.x] * normal +
    weights.y * jointMatrices.data[joints.y] * normal +
    weights.z * jointMatrices.data[joints.z] * normal +
    weights.w * jointMatrices.data[joints.w] * normal;
    dstVector.data[baseIdxSrcBuf] = normal.x / normal.w;
    dstVector.data[baseIdxSrcBuf + 1] = normal.y / normal.w;
    dstVector.data[baseIdxSrcBuf + 2] = normal.z / normal.w;

    baseIdxSrcBuf += 3;
    vec4 tangent = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    tangent =
    weights.x * jointMatrices.data[joints.x] * tangent +
    weights.y * jointMatrices.data[joints.y] * tangent +
    weights.z * jointMatrices.data[joints.z] * tangent +
    weights.w * jointMatrices.data[joints.w] * tangent;
    dstVector.data[baseIdxSrcBuf] = tangent.x / tangent.w;
    dstVector.data[baseIdxSrcBuf + 1] = tangent.y / tangent.w;
    dstVector.data[baseIdxSrcBuf + 2] = tangent.z / tangent.w;

    baseIdxSrcBuf += 3;
    vec4 bitangent = vec4(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2], 0);
    bitangent =
    weights.x * jointMatrices.data[joints.x] * bitangent +
    weights.y * jointMatrices.data[joints.y] * bitangent +
    weights.z * jointMatrices.data[joints.z] * bitangent +
    weights.w * jointMatrices.data[joints.w] * bitangent;
    dstVector.data[baseIdxSrcBuf] = bitangent.x / bitangent.w;
    dstVector.data[baseIdxSrcBuf + 1] = bitangent.y / bitangent.w;
    dstVector.data[baseIdxSrcBuf + 2] = bitangent.z / bitangent.w;

    baseIdxSrcBuf += 3;
    vec2 textCoords = vec2(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1]);
    dstVector.data[baseIdxSrcBuf] = textCoords.x;
    dstVector.data[baseIdxSrcBuf + 1] = textCoords.y;
}