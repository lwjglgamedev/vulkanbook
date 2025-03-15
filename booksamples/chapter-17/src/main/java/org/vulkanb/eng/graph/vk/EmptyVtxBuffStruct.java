package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public class EmptyVtxBuffStruct {

    private final VkPipelineVertexInputStateCreateInfo vi;

    public EmptyVtxBuffStruct() {
        vi = VkPipelineVertexInputStateCreateInfo.calloc();
        vi.sType$Default()
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null);
    }

    public void cleanup() {
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}