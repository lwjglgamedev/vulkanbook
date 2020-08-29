package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class MemoryAllocator {

    private Device device;
    private long vmaAllocator;

    public MemoryAllocator(Instance instance, Device device) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            VmaVulkanFunctions vmaVulkanFunctions = VmaVulkanFunctions.callocStack(stack)
                    .set(instance.getVkInstance(), device.getVkDevice());

            VmaAllocatorCreateInfo createInfo = VmaAllocatorCreateInfo.callocStack(stack)
                    .flags(bitIf(VMA_ALLOCATOR_CREATE_KHR_DEDICATED_ALLOCATION_BIT,
                            device.getVkDevice().getCapabilities().VK_KHR_dedicated_allocation))
                    .device(device.getVkDevice())
                    .physicalDevice(device.getPhysicalDevice().getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAllocator = pAllocator.get(0);
        }
    }

    private static int bitIf(int bit, boolean condition) {
        return condition ? bit : 0;
    }

    public void cleanUp() {
        vmaDestroyAllocator(this.vmaAllocator);
    }

    public Device getDevice() {
        return device;
    }

    public long getVmaAllocator() {
        return vmaAllocator;
    }
}
