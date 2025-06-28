package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class MemAlloc {

    private final long vmaAlloc;

    public MemAlloc(Instance instance, PhysDevice physDevice, Device device) {
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), device.getVkDevice());

            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.getVkInstance())
                    .vulkanApiVersion(VK_API_VERSION_1_3)
                    .device(device.getVkDevice())
                    .physicalDevice(physDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAlloc = pAllocator.get(0);
        }
    }

    public void cleanUp() {
        vmaDestroyAllocator(vmaAlloc);
    }

    public long getVmaAlloc() {
        return vmaAlloc;
    }
}
