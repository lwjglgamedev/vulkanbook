package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix) {
        long mappedMemory = vulkanBuffer.map();
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        matrix.get(0, matrixBuffer);
        vulkanBuffer.unMap();
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

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            throw new RuntimeException(errMsg + ": " + err);
        }
    }
}
