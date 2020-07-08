package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class VulkanBuffer {

    private long allocationSize;
    private long buffer;
    private Device device;
    private long memory;
    private long requestedSize;

    public VulkanBuffer(Device device, long size, int usage, int reqMask) {
        this.device = device;
        this.requestedSize = size;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateBuffer(device.getVkDevice(), bufferCreateInfo, null, lp), "Failed to create vertices buffer");
            this.buffer = lp.get(0);

            VkMemoryRequirements memReqs = VkMemoryRequirements.mallocStack(stack);
            vkGetBufferMemoryRequirements(device.getVkDevice(), this.buffer, memReqs);

            VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                            memReqs.memoryTypeBits(), reqMask));

            vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
            this.allocationSize = memAlloc.allocationSize();
            this.memory = lp.get(0);

            vkCheck(vkBindBufferMemory(device.getVkDevice(), this.buffer, this.memory, 0), "Failed to bind buffer memory");
        }
    }

    public void cleanUp() {
        vkDestroyBuffer(this.device.getVkDevice(), this.buffer, null);
        vkFreeMemory(this.device.getVkDevice(), this.memory, null);
    }

    public long getAllocationSize() {
        return allocationSize;
    }

    public long getBuffer() {
        return buffer;
    }

    public long getMemory() {
        return memory;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

}