package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.Locale;

import static org.lwjgl.vulkan.VK13.*;

public class VkUtils {

    public static final int FLOAT_SIZE = 4;
    public static final int INT_SIZE = 4;
    public static final int MAX_IN_FLIGHT = 2;

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

    public static void imageBarrier(MemoryStack stack, VkCommandBuffer cmdHandle, long image, int oldLayout, int newLayout,
                                    long srcStage, long dstStage, long srcAccess, long dstAccess, int aspectMask) {
        var imageBarrier = VkImageMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcStageMask(srcStage)
                .dstStageMask(dstStage)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .subresourceRange(it -> it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(VK_REMAINING_MIP_LEVELS)
                        .baseArrayLayer(0)
                        .layerCount(VK_REMAINING_ARRAY_LAYERS))
                .image(image);

        VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(imageBarrier);

        vkCmdPipelineBarrier2(cmdHandle, depInfo);
    }

    public static int memoryTypeFromProperties(VkCtx vkCtx, int typeBits, int reqsMask) {
        int result = -1;
        VkMemoryType.Buffer memoryTypes = vkCtx.getPhysDevice().getVkMemoryProperties().memoryTypes();
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
