package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Fence {

    private Device device;
    private long vkFence;

    public Fence(Device device, boolean signaled) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateFence(device.getVkDevice(), fenceCreateInfo, null, lp),
                    "Failed to create semaphore");
            this.vkFence = lp.get(0);
        }
    }

    public void cleanUp() {
        vkDestroyFence(this.device.getVkDevice(), this.vkFence, null);
    }

    public void fenceWait() {
        vkWaitForFences(this.device.getVkDevice(), this.vkFence, true, Long.MAX_VALUE);
    }

    public long getVkFence() {
        return this.vkFence;
    }

    public void reset() {
        vkResetFences(this.device.getVkDevice(), this.vkFence);
    }

}
