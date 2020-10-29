package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class VulkanBuffer {

    private long allocation;
    private long buffer;
    private Device device;
    private long mappedMemory;
    private PointerBuffer pb;
    private long requestedSize;

    public VulkanBuffer(Device device, long size, int bufferUsage, int memoryUsage,
                        int requiredFlags) {
        this.device = device;
        requestedSize = size;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.callocStack(stack)
                    .requiredFlags(requiredFlags)
                    .usage(memoryUsage);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateBuffer(device.getMemoryAllocator().getVmaAllocator(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            buffer = lp.get(0);
            allocation = pAllocation.get(0);
            pb = PointerBuffer.allocateDirect(1);
        }
    }

    public void cleanup() {
        pb.free();
        unMap();
        vmaDestroyBuffer(device.getMemoryAllocator().getVmaAllocator(), buffer, allocation);
    }

    public long getBuffer() {
        return buffer;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

    public long map() {
        if (mappedMemory == NULL) {
            vkCheck(vmaMapMemory(device.getMemoryAllocator().getVmaAllocator(), allocation, pb),
                    "Failed to map allocation");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unMap() {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(device.getMemoryAllocator().getVmaAllocator(), allocation);
            mappedMemory = NULL;
        }
    }
}