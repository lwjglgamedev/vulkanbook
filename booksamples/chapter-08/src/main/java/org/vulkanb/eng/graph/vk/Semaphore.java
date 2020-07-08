package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Semaphore {

    private Device device;
    private long vkSemaphore;

    public Semaphore(Device device) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSemaphore(device.getVkDevice(), semaphoreCreateInfo, null, lp),
                    "Failed to create semaphore");
            this.vkSemaphore = lp.get(0);
        }
    }

    public void cleanUp() {
        vkDestroySemaphore(this.device.getVkDevice(), this.vkSemaphore, null);
    }

    public long getVkSemaphore() {
        return this.vkSemaphore;
    }
}
