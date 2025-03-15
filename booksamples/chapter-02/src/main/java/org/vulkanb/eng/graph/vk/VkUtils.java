package org.vulkanb.eng.graph.vk;

import java.util.Locale;

import static org.lwjgl.vulkan.VK13.*;

public class VkUtils {

    private VkUtils() {
        // Utility class
    }

    public static OSType getOS() {
        OSType result;
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((os.contains("mac")) || (os.contains("darwin"))) {
            result = OSType.MACOS;
        } else if (os.contains("win")) {
            result = OSType.WINDOWS;
        } else if (os.contains("nux")) {
            result = OSType.LINUX;
        } else {
            result = OSType.OTHER;
        }

        return result;
    }

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            String errCode = switch (err) {
                case VK_NOT_READY -> "VK_NOT_READY";
                case VK_TIMEOUT -> "VK_TIMEOUT";
                case VK_EVENT_SET -> "VK_EVENT_SET";
                case VK_EVENT_RESET -> "VK_EVENT_RESET";
                case VK_INCOMPLETE -> "VK_INCOMPLETE";
                case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
                case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
                case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
                case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
                case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
                case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
                case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
                default -> "Not mapped";
            };
            throw new RuntimeException(errMsg + ": " + errCode + " [" + err + "]");
        }
    }

    public enum OSType {WINDOWS, MACOS, LINUX, OTHER}
}
