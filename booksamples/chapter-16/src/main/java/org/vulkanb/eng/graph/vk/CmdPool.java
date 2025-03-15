package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class CmdPool {
    private final long vkCommandPool;

    public CmdPool(VkCtx vkCtx, int queueFamilyIndex, boolean supportReset) {
        Logger.debug("Creating Vulkan command pool");

        try (var stack = MemoryStack.stackPush()) {
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamilyIndex);
            if (supportReset) {
                cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateCommandPool(vkCtx.getDevice().getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            vkCommandPool = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying Vulkan command pool");
        vkDestroyCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, null);
    }

    public long getVkCommandPool() {
        return vkCommandPool;
    }

    public void reset(VkCtx vkCtx) {
        vkResetCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, 0);
    }
}
