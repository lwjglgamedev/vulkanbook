package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class Fence {

    private final long vkFence;

    public Fence(VkCtx vkCtx, boolean signaled) {
        try (var stack = MemoryStack.stackPush()) {
            var fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateFence(vkCtx.getDevice().getVkDevice(), fenceCreateInfo, null, lp), "Failed to create fence");
            vkFence = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroyFence(vkCtx.getDevice().getVkDevice(), vkFence, null);
    }

    public void fenceWait(VkCtx vkCtx) {
        vkWaitForFences(vkCtx.getDevice().getVkDevice(), vkFence, true, Long.MAX_VALUE);
    }

    public long getVkFence() {
        return vkFence;
    }

    public void reset(VkCtx vkCtx) {
        vkResetFences(vkCtx.getDevice().getVkDevice(), vkFence);
    }
}