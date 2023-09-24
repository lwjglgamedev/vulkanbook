package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class CommandPool {

    private final Device device;
    private final long vkCommandPool;

    public CommandPool(Device device, int queueFamilyIndex) {
        Logger.debug("Creating Vulkan CommandPool");

        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateCommandPool(device.getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            vkCommandPool = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroyCommandPool(device.getVkDevice(), vkCommandPool, null);
    }

    public Device getDevice() {
        return device;
    }

    public long getVkCommandPool() {
        return vkCommandPool;
    }
}
