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

    mat3 matJoint1 = mat3(transpose(inverse(jointMatrices.data[joints.x])));
    mat3 matJoint2 = mat3(transpose(inverse(jointMatrices.data[joints.y])));
    mat3 matJoint3 = mat3(transpose(inverse(jointMatrices.data[joints.z])));
    baseIdxSrcBuf += 3;
    vec3 normal = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    normal =
    weights.x * matJoint1 * normal +
    weights.y * matJoint2 * normal +
    weights.z * matJoint3 * normal;
    normal = normalize(normal);
    dstVector.data[baseIdxSrcBuf] = normal.x;
    dstVector.data[baseIdxSrcBuf + 1] = normal.y;
    dstVector.data[baseIdxSrcBuf + 2] = normal.z;

    baseIdxSrcBuf += 3;
    vec3 tangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    tangent =
    weights.x * matJoint1 * tangent +
    weights.y * matJoint2 * tangent +
    weights.z * matJoint3 * tangent;
    tangent = normalize(tangent);
    dstVector.data[baseIdxSrcBuf] = tangent.x;
    dstVector.data[baseIdxSrcBuf + 1] = tangent.y;
    dstVector.data[baseIdxSrcBuf + 2] = tangent.z;

    baseIdxSrcBuf += 3;
    vec3 bitangent = vec3(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1], srcVector.data[baseIdxSrcBuf + 2]);
    bitangent =
    weights.x * matJoint1 * bitangent +
    weights.y * matJoint2 * bitangent +
    weights.z * matJoint3 * bitangent;
    bitangent = normalize(bitangent);
    dstVector.data[baseIdxSrcBuf] = bitangent.x;
    dstVector.data[baseIdxSrcBuf + 1] = bitangent.y;
    dstVector.data[baseIdxSrcBuf + 2] = bitangent.z;

    baseIdxSrcBuf += 3;
    vec2 textCoords = vec2(srcVector.data[baseIdxSrcBuf], srcVector.data[baseIdxSrcBuf + 1]);
    dstVector.data[baseIdxSrcBuf] = textCoords.x;
    dstVector.data[baseIdxSrcBuf + 1] = textCoords.y;
}