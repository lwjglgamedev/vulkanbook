package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.AMDBufferMarker.vkCmdWriteBufferMarkerAMD;
import static org.lwjgl.vulkan.NVDeviceDiagnosticCheckpoints.*;
import static org.lwjgl.vulkan.VK11.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix, int offset) {
        long mappedMemory = vulkanBuffer.map();
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        matrix.get(offset, matrixBuffer);
        vulkanBuffer.unMap();
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix) {
        copyMatrixToBuffer(vulkanBuffer, matrix, 0);
    }

    public static List<CheckPoint> dumpCheckPoints(Queue queue) {
        List<CheckPoint> result = new ArrayList<>();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.callocInt(1);
            vkGetQueueCheckpointDataNV(queue.getVkQueue(), count, null);
            int numCheckPoints = count.get(0);
            if (numCheckPoints > 0) {
                VkCheckpointDataNV.Buffer checkpointData = VkCheckpointDataNV.calloc(numCheckPoints, stack);
                checkpointData.stream().forEach(c -> c.sType(VK_STRUCTURE_TYPE_CHECKPOINT_DATA_NV));

                vkGetQueueCheckpointDataNV(queue.getVkQueue(), count, checkpointData);
                checkpointData.forEach(c -> result.add(new CheckPoint(c.pCheckpointMarker(), c.stage())));
            }
        }
        return result;
    }

    public static void insertBufferMarker(Device device, CommandBuffer cmdBuff, int pipelineStage, VulkanBuffer dstBuffer,
                                          int offset, int marker) {
        PhysicalDevice.CheckPointExtension checkPointExtension = device.getPhysicalDevice().getCheckPointExtension();
        if (checkPointExtension == PhysicalDevice.CheckPointExtension.AMD) {
            vkCmdWriteBufferMarkerAMD(cmdBuff.getVkCommandBuffer(), pipelineStage, dstBuffer.getBuffer(), offset,
                    marker);
        } else {
            Logger.warn("Requested debug buffer marker in non supported device");
        }
    }

    public static void insertDebugCheckPoint(Device device, CommandBuffer cmdBuff, long checkPointMarker) {
        PhysicalDevice.CheckPointExtension checkPointExtension = device.getPhysicalDevice().getCheckPointExtension();
        if (checkPointExtension == PhysicalDevice.CheckPointExtension.NVIDIA) {
            vkCmdSetCheckpointNV(cmdBuff.getVkCommandBuffer(), checkPointMarker);
        } else {
            Logger.warn("Requested debug check point in non supported device");
        }
    }

    public static int memoryTypeFromProperties(PhysicalDevice physDevice, int typeBits, int reqsMask) {
        int result = -1;
        VkMemoryType.Buffer memoryTypes = physDevice.getVkMemoryProperties().memoryTypes();
        for (int i = 0; i < VK_MAX_MEMORY_TYPES; i++) {
            if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqsMask) == reqsMask) {
                result = i;
                break;
            }
            typeBits >>= 1;
        }
        if (result < 0) {
            throw new RuntimeException("Failed to find memoryType");
        }
        return result;
    }

    public static void setMatrixAsPushConstant(Pipeline pipeLine, VkCommandBuffer cmdHandle, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE);
            matrix.get(0, pushConstantBuffer);
            vkCmdPushConstants(cmdHandle, pipeLine.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
        }
    }

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            throw new RuntimeException(errMsg + ": " + err);
        }
    }

    public record CheckPoint(long marker, int stage) {
    }
}
