package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkMemoryBarrier;

import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_MEMORY_BARRIER;

public class MemoryBarrier {

    private final VkMemoryBarrier.Buffer vkMemoryBarrier;

    public MemoryBarrier(int srcAccessMask, int dstAccessMask) {
        vkMemoryBarrier = VkMemoryBarrier.calloc(1)
                .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
    }

    public void cleanup() {
        MemoryUtil.memFree(vkMemoryBarrier);
    }

    public VkMemoryBarrier.Buffer getVkMemoryBarrier() {
        return vkMemoryBarrier;
    }
}