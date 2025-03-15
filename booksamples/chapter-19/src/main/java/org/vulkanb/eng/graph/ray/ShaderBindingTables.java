package org.vulkanb.eng.graph.ray;

import org.vulkanb.eng.graph.vk.VkCtx;

public record ShaderBindingTables(ShaderBindingTable rayGen, ShaderBindingTable miss, ShaderBindingTable hit) {

    public void cleanup(VkCtx vkCtx) {
        rayGen.cleanup(vkCtx);
        miss.cleanup(vkCtx);
        hit.cleanup(vkCtx);
    }
}
