package org.vulkanb.eng.graph.scn;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.VkUtils;

import static org.lwjgl.vulkan.VK13.*;

public class VtxBuffStruct {
    private static final int NUMBER_OF_ATTRIBUTES = 1;
    private static final int POSITION_COMPONENTS = 3;

    private final VkPipelineVertexInputStateCreateInfo vi;
    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public VtxBuffStruct() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        int offset = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(offset);

        viBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * VkUtils.FLOAT_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        vi
                .sType$Default()
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    public void cleanup() {
        viBindings.free();
        viAttrs.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
