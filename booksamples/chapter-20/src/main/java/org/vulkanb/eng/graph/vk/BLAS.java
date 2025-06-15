package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4x3f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class BLAS {

    private static final int STRIDE = VkUtils.VEC3_SIZE * 4 + VkUtils.VEC2_SIZE;
    private final VkBuffer buffBlas;
    private final long deviceAddress;
    private final long handle;

    public BLAS(VkCtx vkCtx, VulkanModel vulkanModel, CmdPool cmdPool, Queue queue) {
        try (var stack = MemoryStack.stackPush()) {
            var vkDevice = vkCtx.getDevice().getVkDevice();

            List<VkBuffer> transformBuffList = new ArrayList<>();
            List<Integer> maxPrimitiveCounts = new ArrayList<>();

            List<VulkanMesh> meshList = vulkanModel.getVulkanMeshList();
            int numMeshes = meshList.size();
            var accelStGeom = VkAccelerationStructureGeometryKHR.calloc(numMeshes, stack);
            var buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(numMeshes, stack);
            for (int i = 0; i < numMeshes; i++) {
                var vulkanMesh = meshList.get(i);
                VkBuffer transformBuff = createTransformBuffer(vkCtx);
                var vtxBuffAddress = VkUtils.getBufferAddressConst(stack, vkCtx, vulkanMesh.verticesBuffer().getBuffer());
                var idxBuffAddress = VkUtils.getBufferAddressConst(stack, vkCtx, vulkanMesh.indicesBuffer().getBuffer());
                var transformBuffAddress = VkUtils.getBufferAddressConst(stack, vkCtx, transformBuff.getBuffer());
                transformBuffList.add(transformBuff);

                // The bottom level acceleration structure contains one set of triangles as the input geometry
                accelStGeom.get(i)
                        .sType$Default()
                        .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
                        .flags(VK_GEOMETRY_NO_DUPLICATE_ANY_HIT_INVOCATION_BIT_KHR);
                accelStGeom.get(i).geometry().triangles()
                        .sType$Default()
                        .vertexFormat(VK_FORMAT_R32G32B32_SFLOAT)
                        .vertexData(vtxBuffAddress)
                        .maxVertex(vulkanMesh.numVertices())
                        .vertexStride(STRIDE)
                        .indexType(VK_INDEX_TYPE_UINT32)
                        .indexData(idxBuffAddress)
                        .transformData(transformBuffAddress);
                int primitiveCount = vulkanMesh.numIndices() / 3;
                maxPrimitiveCounts.add(primitiveCount);

                buildRangeInfo.get(i)
                        .firstVertex(0)
                        .primitiveOffset(0)
                        .primitiveCount(primitiveCount)
                        .transformOffset(0);
            }

            // Get the size requirements for buffers involved in the acceleration structure build process
            var accelStBuildGeom = VkAccelerationStructureBuildGeometryInfoKHR.calloc(stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .geometryCount(accelStGeom.remaining())
                    .pGeometries(accelStGeom);

            IntBuffer primitiveCountsBuffer = stack.mallocInt(maxPrimitiveCounts.size());
            for (int i = 0; i < maxPrimitiveCounts.size(); i++) {
                primitiveCountsBuffer.put(i, maxPrimitiveCounts.get(i));
            }

            var accelStBuildSizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
                    .sType$Default();
            vkGetAccelerationStructureBuildSizesKHR(vkDevice, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    accelStBuildGeom, primitiveCountsBuffer, accelStBuildSizes);

            // Create a buffer to hold the acceleration structure
            buffBlas = new VkBuffer(vkCtx, accelStBuildSizes.accelerationStructureSize(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            // Create the acceleration structure
            var accelStCreateInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .buffer(buffBlas.getBuffer())
                    .size(buffBlas.getRequestedSize())
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);

            var lp = stack.callocLong(1);
            vkCheck(vkCreateAccelerationStructureKHR(vkDevice, accelStCreateInfo, null, lp),
                    "Failed to create acceleration structure");
            handle = lp.get(0);

            // The actual build process starts here

            // Create a scratch buffer as a temporary storage for the acceleration structure build
            VkBuffer scratchBuff = new VkBuffer(vkCtx, accelStBuildSizes.buildScratchSize(),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            var accelBuildGeomInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .dstAccelerationStructure(handle)
                    .geometryCount(accelStGeom.remaining())
                    .pGeometries(accelStGeom);
            accelBuildGeomInfo.scratchData().deviceAddress(VkUtils.getBufferAddress(vkCtx, scratchBuff.getBuffer()));

            // Build the acceleration structure on the device via a one-time command buffer submission
            CmdBuffer cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmdBuffer.beginRecording();
            vkCmdBuildAccelerationStructuresKHR(cmdBuffer.getVkCommandBuffer(), accelBuildGeomInfo,
                    stack.pointers(buildRangeInfo));
            cmdBuffer.endRecording();
            cmdBuffer.submitAndWait(vkCtx, queue);

            var accelStDeviceAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
                    .sType$Default()
                    .accelerationStructure(handle);
            deviceAddress = vkGetAccelerationStructureDeviceAddressKHR(vkDevice, accelStDeviceAddressInfo);

            cmdBuffer.cleanup(vkCtx, cmdPool);
            scratchBuff.cleanup(vkCtx);
            transformBuffList.forEach(b -> b.cleanup(vkCtx));
        }
    }

    private static VkBuffer createTransformBuffer(VkCtx vkCtx) {
        int transformBuffSize = VkUtils.FLOAT_SIZE * 12;
        var transformBuff = new VkBuffer(vkCtx, transformBuffSize,
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR,
                VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var matrix = new Matrix4x3f().identity();
        long mappedMemory = transformBuff.map(vkCtx);
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) transformBuff.getRequestedSize());
        matrix.getTransposed(0, matrixBuffer);
        transformBuff.unMap(vkCtx);

        return transformBuff;
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyAccelerationStructureKHR(vkCtx.getDevice().getVkDevice(), handle, null);
        buffBlas.cleanup(vkCtx);
    }

    public long getDeviceAddress() {
        return deviceAddress;
    }
}
