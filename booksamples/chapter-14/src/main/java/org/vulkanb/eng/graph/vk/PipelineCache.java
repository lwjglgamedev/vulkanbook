package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class PipelineCache {
    private final long vkPipelineCache;

    public PipelineCache(Device device) {
        Logger.debug("Creating pipeline cache");
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Error creating pipeline cache");
            vkPipelineCache = lp.get(0);
        }
    }

    public void cleanup(Device device) {
        Logger.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(device.getVkDevice(), vkPipelineCache, null);
    }

    public long getVkPipelineCache() {
        return vkPipelineCache;
    }
}
