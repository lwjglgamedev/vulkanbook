package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4x3f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.scene.Entity;

import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRAccelerationStructure.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class TLAS {

    private final VkAccelerationStructureInstanceKHR.Buffer accelStInstances;
    private final VkBuffer buffTlas;
    private final long deviceAddress;
    private final long handle;
    private final Matrix4x3f tmpMatrix;
    private Map<String, Integer> accelIndexMap = new HashMap<>();
    private long lastUpdateTime;

    public TLAS(VkCtx vkCtx, List<Entity> entities, Map<String, BLAS> blasMap, List<Integer> modelsOffsets,
                CmdPool cmdPool, Queue queue) {
        lastUpdateTime = System.nanoTime();
        try (var stack = MemoryStack.stackPush()) {
            var vkDevice = vkCtx.getDevice().getVkDevice();
            tmpMatrix = new Matrix4x3f();

            int numEntities = entities.size();
            accelStInstances = VkAccelerationStructureInstanceKHR.calloc(numEntities);
            for (int i = 0; i < numEntities; i++) {
                Entity entity = entities.get(i);
                accelIndexMap.put(entity.getModelId(), i);
                BLAS blas = blasMap.get(entity.getModelId());

                tmpMatrix.set(entity.getModelMatrix());
                var transformMatrix = VkTransformMatrixKHR
                        .calloc(stack)
                        .matrix(tmpMatrix.getTransposed(stack.callocFloat(4 * 3)));
                accelStInstances.get(i)
                        .instanceCustomIndex(modelsOffsets.get(i))
                        .mask(0xFF)
                        .instanceShaderBindingTableRecordOffset(0)
                        .flags(VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR)
                        .accelerationStructureReference(blas.getDeviceAddress())
                        .transform(transformMatrix);
            }

            var instanceData = MemoryUtil.memByteBuffer(accelStInstances);
            var buffInstance = new VkBuffer(vkCtx, instanceData.remaining(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

            VkUtils.copyBufferToBuffer(vkCtx, instanceData, buffInstance);

            CmdBuffer cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmdBuffer.beginRecording();
            var memBarrier = VkMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR);
            var depInfo = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(memBarrier);

            vkCmdPipelineBarrier2(cmdBuffer.getVkCommandBuffer(), depInfo);

            var instanceDeviceAddress = VkUtils.getBufferAddressConst(stack, vkCtx, buffInstance.getBuffer());

            var instancesVk = VkAccelerationStructureGeometryInstancesDataKHR.calloc(stack)
                    .sType$Default()
                    .data(instanceDeviceAddress);

            // The top level acceleration structure contains (bottom level) instance as the input geometry
            var topASGeometry = VkAccelerationStructureGeometryKHR.calloc(1, stack)
                    .sType$Default()
                    .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR);
            topASGeometry.geometry().instances(instancesVk);

            // Get the size requirements for buffers involved in the acceleration structure build process
            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR | VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .geometryCount(1)
                    .pGeometries(topASGeometry);

            var accelStBuildSizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
                    .sType$Default();
            vkGetAccelerationStructureBuildSizesKHR(vkDevice, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    buildInfo.get(0), stack.ints(numEntities), accelStBuildSizes);

            // Create a buffer to hold the acceleration structure
            buffTlas = new VkBuffer(vkCtx, accelStBuildSizes.accelerationStructureSize(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            // Create the acceleration structure
            var accelStCreateInfo = VkAccelerationStructureCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .buffer(buffTlas.getBuffer())
                    .size(buffTlas.getRequestedSize())
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);

            var lp = stack.callocLong(1);
            vkCheck(vkCreateAccelerationStructureKHR(vkDevice, accelStCreateInfo, null, lp),
                    "Failed to create acceleration structure");
            handle = lp.get(0);

            // The actual build process starts here

            // Create a scratch buffer as a temporary storage for the acceleration structure build
            VkBuffer scratchBuff = new VkBuffer(vkCtx, accelStBuildSizes.buildScratchSize(),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            buildInfo.srcAccelerationStructure(VK_NULL_HANDLE);
            buildInfo.dstAccelerationStructure(handle);
            buildInfo.scratchData().deviceAddress(VkUtils.getBufferAddress(vkCtx, scratchBuff.getBuffer()));

            var accelStBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack)
                    .primitiveCount(numEntities)
                    .primitiveOffset(0)
                    .firstVertex(0)
                    .transformOffset(0);

            // Build the acceleration structure on the device via a one-time command buffer submission
            vkCmdBuildAccelerationStructuresKHR(cmdBuffer.getVkCommandBuffer(), buildInfo,
                    stack.pointersOfElements(accelStBuildRangeInfo));
            cmdBuffer.endRecording();
            cmdBuffer.submitAndWait(vkCtx, queue);

            cmdBuffer.cleanup(vkCtx, cmdPool);
            scratchBuff.cleanup(vkCtx);
            buffInstance.cleanup(vkCtx);

            // Get the top acceleration structure's handle, which will be used to setup it's descriptor
            var accelStDeviceAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
                    .sType$Default()
                    .accelerationStructure(handle);
            deviceAddress = vkGetAccelerationStructureDeviceAddressKHR(vkDevice, accelStDeviceAddressInfo);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyAccelerationStructureKHR(vkCtx.getDevice().getVkDevice(), handle, null);
        buffTlas.cleanup(vkCtx);
        MemoryUtil.memFree(accelStInstances);
    }

    public long getDeviceAddress() {
        return deviceAddress;
    }

    public long getHandle() {
        return handle;
    }

    public boolean update(VkCtx vkCtx, List<Entity> entities, CmdPool cmdPool, Queue queue) {
        // Update matrices
        boolean update = false;
        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            Entity entity = entities.get(i);
            if (entity.getUpdateTime() > lastUpdateTime) {
                update = true;
                break;
            }
        }
        if (!update) {
            return false;
        }
        lastUpdateTime = System.nanoTime();

        try (var stack = MemoryStack.stackPush()) {
            var vkDevice = vkCtx.getDevice().getVkDevice();
            for (int i = 0; i < numEntities; i++) {
                Entity entity = entities.get(i);
                tmpMatrix.set(entity.getModelMatrix());
                var transformMatrix = VkTransformMatrixKHR
                        .calloc(stack)
                        .matrix(tmpMatrix.getTransposed(stack.callocFloat(4 * 3)));
                accelStInstances.get(i)
                        .transform(transformMatrix);
            }

            var instanceData = MemoryUtil.memByteBuffer(accelStInstances);
            var buffInstance = new VkBuffer(vkCtx, instanceData.remaining(),
                    VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

            VkUtils.copyBufferToBuffer(vkCtx, instanceData, buffInstance);

            CmdBuffer cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmdBuffer.beginRecording();
            var memBarrier = VkMemoryBarrier2.calloc(1, stack)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .dstStageMask(VK_PIPELINE_STAGE_ACCELERATION_STRUCTURE_BUILD_BIT_KHR)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_ACCELERATION_STRUCTURE_WRITE_BIT_KHR);
            var depInfo = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(memBarrier);

            vkCmdPipelineBarrier2(cmdBuffer.getVkCommandBuffer(), depInfo);

            var instanceDeviceAddress = VkUtils.getBufferAddressConst(stack, vkCtx, buffInstance.getBuffer());

            var instancesVk = VkAccelerationStructureGeometryInstancesDataKHR.calloc(stack)
                    .sType$Default()
                    .data(instanceDeviceAddress);

            var topASGeometry = VkAccelerationStructureGeometryKHR.calloc(1, stack)
                    .sType$Default()
                    .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR);
            topASGeometry.geometry().instances(instancesVk);

            // Get the size requirements for buffers involved in the acceleration structure build process
            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack)
                    .sType$Default()
                    .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
                    .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR | VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_UPDATE_BIT_KHR)
                    .mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR)
                    .geometryCount(1)
                    .pGeometries(topASGeometry);

            var accelStBuildSizes = VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
                    .sType$Default();
            vkGetAccelerationStructureBuildSizesKHR(vkDevice, VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                    buildInfo.get(0), stack.ints(numEntities), accelStBuildSizes);

            // Create a scratch buffer as a temporary storage for the acceleration structure build
            VkBuffer scratchBuff = new VkBuffer(vkCtx, accelStBuildSizes.updateScratchSize(),
                    VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            buildInfo.mode(VK_BUILD_ACCELERATION_STRUCTURE_MODE_UPDATE_KHR);
            buildInfo.srcAccelerationStructure(handle);
            buildInfo.dstAccelerationStructure(handle);
            buildInfo.scratchData().deviceAddress(VkUtils.getBufferAddress(vkCtx, scratchBuff.getBuffer()));
            buildInfo.pGeometries(topASGeometry);

            var accelStBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack)
                    .primitiveCount(numEntities)
                    .primitiveOffset(0)
                    .firstVertex(0)
                    .transformOffset(0);

            // Build the acceleration structure on the device via a one-time command buffer submission
            vkCmdBuildAccelerationStructuresKHR(cmdBuffer.getVkCommandBuffer(), buildInfo,
                    stack.pointersOfElements(accelStBuildRangeInfo));
            cmdBuffer.endRecording();
            cmdBuffer.submitAndWait(vkCtx, queue);

            cmdBuffer.cleanup(vkCtx, cmdPool);
            scratchBuff.cleanup(vkCtx);
            buffInstance.cleanup(vkCtx);
        }
        return true;
    }
}