package org.vulkanb.eng.graph.scn;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public class VtxBuffStruct {

    private final VkPipelineVertexInputStateCreateInfo vi;

    public VtxBuffStruct() {
        vi = VkPipelineVertexInputStateCreateInfo.calloc().sType$Default();
    }

    public void cleanup() {
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
