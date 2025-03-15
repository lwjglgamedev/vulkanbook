package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK13.VK_SHARING_MODE_EXCLUSIVE;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class VkBuffer {

    private final long allocation;
    private final long buffer;
    private final PointerBuffer pb;
    private final long requestedSize;

    private long mappedMemory;

    public VkBuffer(VkCtx vkCtx, long size, int bufferUsage, int vmaUsage, int vmaFlags, int reqFlags) {
        requestedSize = size;
        mappedMemory = NULL;
        try (var stack = MemoryStack.stackPush()) {
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(vmaUsage)
                    .flags(vmaFlags)
                    .requiredFlags(reqFlags);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateBuffer(vkCtx.getMemAlloc().getVmaAlloc(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            buffer = lp.get(0);
            allocation = pAllocation.get(0);
            pb = MemoryUtil.memAllocPointer(1);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pb);
        unMap(vkCtx);
        vmaDestroyBuffer(vkCtx.getMemAlloc().getVmaAlloc(), buffer, allocation);
    }

    public void flush(VkCtx vkCtx) {
        vmaFlushAllocation(vkCtx.getMemAlloc().getVmaAlloc(), allocation, 0, VK_WHOLE_SIZE);
    }

    public long getBuffer() {
        return buffer;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

    public long map(VkCtx vkCtx) {
        if (mappedMemory == NULL) {
            vkCheck(vmaMapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation, pb), "Failed to map buffer");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unMap(VkCtx vkCtx) {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation);
            mappedMemory = NULL;
        }
    }
}