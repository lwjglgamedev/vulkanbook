package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class VkBuffer {

    private final long allocationSize;
    private final long buffer;
    private final long memory;
    private final PointerBuffer pb;
    private final long requestedSize;

    private long mappedMemory;

    public VkBuffer(VkCtx vkCtx, long size, int usage, int reqMask) {
        requestedSize = size;
        mappedMemory = NULL;
        try (var stack = MemoryStack.stackPush()) {
            Device device = vkCtx.getDevice();
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateBuffer(device.getVkDevice(), bufferCreateInfo, null, lp), "Failed to create buffer");
            buffer = lp.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device.getVkDevice(), buffer, memReqs);

            var memAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VkUtils.memoryTypeFromProperties(vkCtx, memReqs.memoryTypeBits(), reqMask));

            vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
            allocationSize = memAlloc.allocationSize();
            memory = lp.get(0);
            pb = MemoryUtil.memAllocPointer(1);

            vkCheck(vkBindBufferMemory(device.getVkDevice(), buffer, memory, 0), "Failed to bind buffer memory");
        }
    }

    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pb);
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();
        vkDestroyBuffer(vkDevice, buffer, null);
        vkFreeMemory(vkDevice, memory, null);
    }

    public void flush(VkCtx vkCtx) {
        try (var stack = MemoryStack.stackPush()) {
            VkMappedMemoryRange mappedRange = VkMappedMemoryRange.calloc(stack)
                    .sType$Default()
                    .memory(memory)
                    .size(VK_WHOLE_SIZE);
            vkFlushMappedMemoryRanges(vkCtx.getDevice().getVkDevice(), mappedRange);
        }
    }

    public long getBuffer() {
        return buffer;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

    public long map(VkCtx vkCtx) {
        if (mappedMemory == NULL) {
            vkCheck(vkMapMemory(vkCtx.getDevice().getVkDevice(), memory, 0, allocationSize, 0, pb), "Failed to map Buffer");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unMap(VkCtx vkCtx) {
        if (mappedMemory != NULL) {
            vkUnmapMemory(vkCtx.getDevice().getVkDevice(), memory);
            mappedMemory = NULL;
        }
    }
}