package org.vulkanb.eng.graph.vk;

import org.joml.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkMemoryType;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(Device device, VulkanBuffer vulkanBuffer, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = stack.mallocPointer(1);
            vkCheck(vkMapMemory(device.getVkDevice(), vulkanBuffer.getMemory(), 0, vulkanBuffer.getAllocationSize(),
                    0, pointerBuffer), "Failed to map Buffer");
            long data = pointerBuffer.get(0);
            ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(data, (int) vulkanBuffer.getAllocationSize());
            matrix.get(0, matrixBuffer);
            vkUnmapMemory(device.getVkDevice(), vulkanBuffer.getMemory());
        }
    }

    public static void copyVectortoBuffer(Device device, VulkanBuffer vulkanBuffer, Vector4f vector) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointerBuffer = stack.mallocPointer(1);
            vkCheck(vkMapMemory(device.getVkDevice(), vulkanBuffer.getMemory(), 0, vulkanBuffer.getAllocationSize(),
                    0, pointerBuffer), "Failed to map Buffer");
            long data = pointerBuffer.get(0);
            ByteBuffer uniformBuffer = MemoryUtil.memByteBuffer(data, (int) vulkanBuffer.getRequestedSize());
            vector.get(0, uniformBuffer);
            vkUnmapMemory(device.getVkDevice(), vulkanBuffer.getMemory());
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

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            throw new RuntimeException(errMsg + ": " + err);
        }
    }
}
