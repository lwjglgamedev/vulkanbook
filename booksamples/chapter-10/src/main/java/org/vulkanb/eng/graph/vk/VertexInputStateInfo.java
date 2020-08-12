package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public abstract class VertexInputStateInfo {

    protected VkPipelineVertexInputStateCreateInfo vi;

    public void cleanup() {
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
