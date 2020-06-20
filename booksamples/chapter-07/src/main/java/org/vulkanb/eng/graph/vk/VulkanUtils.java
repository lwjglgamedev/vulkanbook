package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkMemoryType;

import static org.lwjgl.vulkan.VK10.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
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
