package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkMemoryType;

import java.util.Locale;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static OSType getOS() {
        OSType result;
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0)) {
            result = OSType.MACOS;
        } else if (os.indexOf("win") >= 0) {
            result = OSType.WINDOWS;
        } else if (os.indexOf("nux") >= 0) {
            result = OSType.LINUX;
        } else {
            result = OSType.OTHER;
        }

        return result;
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

    public enum OSType {WINDOWS, MACOS, LINUX, OTHER}
}
