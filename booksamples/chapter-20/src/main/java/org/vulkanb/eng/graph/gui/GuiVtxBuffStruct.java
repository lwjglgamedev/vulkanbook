package org.vulkanb.eng.graph.gui;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.VkUtils;

import static org.lwjgl.vulkan.VK13.*;

public class GuiVtxBuffStruct {

    public static final int VERTEX_SIZE = VkUtils.FLOAT_SIZE * 5;
    private static final int NUMBER_OF_ATTRIBUTES = 3;

    private final VkPipelineVertexInputStateCreateInfo vi;
    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public GuiVtxBuffStruct() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        int offset = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(offset);

        // Texture coordinates
        i++;
        offset += VkUtils.FLOAT_SIZE * 2;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(offset);

        // Color
        i++;
        offset += VkUtils.FLOAT_SIZE * 2;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .offset(offset);

        viBindings.get(0)
                .binding(0)
                .stride(VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    public void cleanup() {
        viBindings.free();
        viAttrs.free();
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
