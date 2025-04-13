package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;

public record VulkanMesh(String id, VkBuffer verticesBuffer, VkBuffer indicesBuffer, VkBuffer weightsBuffer,
                         int numIndices, String materialdId) {

    public void cleanup(VkCtx vkCtx) {
        verticesBuffer.cleanup(vkCtx);
        indicesBuffer.cleanup(vkCtx);
        if (weightsBuffer != null) {
            weightsBuffer.cleanup(vkCtx);
        }
    }
}