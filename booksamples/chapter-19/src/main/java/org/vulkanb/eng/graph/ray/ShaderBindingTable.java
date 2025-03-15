package org.vulkanb.eng.graph.ray;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;

public class ShaderBindingTable {

    private final VkBuffer buffer;
    private final VkStridedDeviceAddressRegionKHR stridedDeviceAddressRegionKHR;

    public ShaderBindingTable(VkCtx vkCtx, int handleCount) {
        // Create buffer to hold all shader handles for the SBT
        int size = vkCtx.getPhysDevice().getRayTracingProperties().shaderGroupHandleSize() * handleCount;
        buffer = new VkBuffer(vkCtx, size, VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR |
                VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT_KHR, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

        // Get the strided address to be used when dispatching the rays
        stridedDeviceAddressRegionKHR = getSbtEntryStridedDeviceAddressRegion(vkCtx, buffer, handleCount);
    }

    private static VkStridedDeviceAddressRegionKHR getSbtEntryStridedDeviceAddressRegion(VkCtx vkCtx, VkBuffer buffer,
                                                                                         int handleCount) {
        VkPhysicalDeviceRayTracingPipelinePropertiesKHR props = vkCtx.getPhysDevice().getRayTracingProperties();
        int shaderGroupHandleSize = props.shaderGroupHandleSize();
        int shaderGroupHandleAlignment = props.shaderGroupHandleAlignment();
        int handleSizeAligned = VkUtils.alignUp(shaderGroupHandleSize, shaderGroupHandleAlignment);

        return VkStridedDeviceAddressRegionKHR.calloc()
                .deviceAddress(VkUtils.getBufferAddress(vkCtx, buffer.getBuffer()))
                .stride(handleSizeAligned)
                .size((long) handleCount * handleSizeAligned);
    }

    public void cleanup(VkCtx vkCtx) {
        buffer.cleanup(vkCtx);
        stridedDeviceAddressRegionKHR.free();
    }

    public VkBuffer getBuffer() {
        return buffer;
    }

    public VkStridedDeviceAddressRegionKHR getStridedDeviceAddressRegionKHR() {
        return stridedDeviceAddressRegionKHR;
    }
}
