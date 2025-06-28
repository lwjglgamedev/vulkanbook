package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;

import java.util.List;

public record VulkanAnimation(String name, List<VkBuffer> frameBufferList) {
    public void cleanup(VkCtx vkCtx) {
        frameBufferList.forEach(b -> b.cleanup(vkCtx));
    }
}
