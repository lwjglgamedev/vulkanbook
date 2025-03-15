package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

public class VkUtils {

    public static final int FLOAT_SIZE = 4;
    public static final int INT_SIZE = 4;
    public static final int MAT4X4_SIZE = 16 * FLOAT_SIZE;
    public static final int MAX_IN_FLIGHT = 2;
    public static final int VEC4_SIZE = 4 * FLOAT_SIZE;

    private VkUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VkCtx vkCtx, VkBuffer vkBuffer, Matrix4f matrix, int offset) {
        long mappedMemory = vkBuffer.map(vkCtx);
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vkBuffer.getRequestedSize());
        matrix.get(offset, matrixBuffer);
        vkBuffer.unMap(vkCtx);
    }

    public static VkBuffer createHostVisibleBuff(VkCtx vkCtx, long buffSize, int usage, String id, DescSetLayout layout) {
        var buff = new VkBuffer(vkCtx, buffSize, usage, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        Device device = vkCtx.getDevice();
        DescSet descSet = vkCtx.getDescAllocator().addDescSet(device, id, layout);
        descSet.setBuffer(device, buff, buff.getRequestedSize(), layout.getLayoutInfo().binding(),
                layout.getLayoutInfo().descType());
        return buff;
    }

    public static VkBuffer[] createHostVisibleBuffs(VkCtx vkCtx, long buffSize, int numBuffs, int usage,
                                                    String id, DescSetLayout layout) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        VkBuffer[] result = new VkBuffer[numBuffs];
        descAllocator.addDescSets(device, id, numBuffs, layout);
        DescSetLayout.LayoutInfo layoutInfo = layout.getLayoutInfo();
        for (int i = 0; i < numBuffs; i++) {
            result[i] = new VkBuffer(vkCtx, buffSize, usage, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            DescSet descSet = descAllocator.getDescSet(id, i);
            descSet.setBuffer(device, result[i], result[i].getRequestedSize(), layoutInfo.binding(), layoutInfo.descType());
        }
        return result;
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
