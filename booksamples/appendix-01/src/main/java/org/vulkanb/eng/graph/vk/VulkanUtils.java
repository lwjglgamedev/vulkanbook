package org.vulkanb.eng.graph.vk;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix) {
        copyMatrixToBuffer(vulkanBuffer, matrix, 0);
    }

    public static void copyMatrixToBuffer(VulkanBuffer vulkanBuffer, Matrix4f matrix, int offset) {
        long mappedMemory = vulkanBuffer.map();
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        matrix.get(offset, matrixBuffer);
        vulkanBuffer.unMap();
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

    public enum OSType {WINDOWS, MACOS, LINUX, OTHER}
}
