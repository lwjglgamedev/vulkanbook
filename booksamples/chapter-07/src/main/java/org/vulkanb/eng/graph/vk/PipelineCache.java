package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class PipelineCache {
    private static final Logger LOGGER = LogManager.getLogger();
    private Device device;
    private long vkPipelineCache;

    public PipelineCache(Device device) {
        LOGGER.debug("Creating pipeline cache");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo createInfo = VkPipelineCacheCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Error creating pipeline cache");
            this.vkPipelineCache = lp.get(0);
        }
    }

    public void cleanUp() {
        LOGGER.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(this.device.getVkDevice(), this.vkPipelineCache, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkPipelineCache() {
        return this.vkPipelineCache;
    }
}
