package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;

public record VulkanMesh(String id, VkBuffer verticesBuffer, VkBuffer indicesBuffer, int numIndices,
                         String materialdId) {
    public void cleanup(VkCtx vkCtx) {
        verticesBuffer.cleanup(vkCtx);
        indicesBuffer.cleanup(vkCtx);
    }
}