package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class CommandPool {
    private static final Logger LOGGER = LogManager.getLogger();
    private Device device;
    private long vkCommandPool;

    public CommandPool(Device device, int queueFamilyIndex) {
        LOGGER.debug("Creating Vulkan CommandPool");

        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo cmdPoolInfo = VkCommandPoolCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateCommandPool(device.getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            this.vkCommandPool = lp.get(0);
        }
    }

    public void cleanUp() {
        vkDestroyCommandPool(this.device.getVkDevice(), this.vkCommandPool, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkCommandPool() {
        return this.vkCommandPool;
    }
}
