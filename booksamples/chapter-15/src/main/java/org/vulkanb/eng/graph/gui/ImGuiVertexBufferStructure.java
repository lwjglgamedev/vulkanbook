package org.vulkanb.eng.graph.gui;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import static org.lwjgl.vulkan.VK10.*;

public class ImGuiVertexBufferStructure extends VertexInputStateInfo {

    public static final int VERTEX_SIZE = GraphConstants.FLOAT_LENGTH * 5;
    private static final int NUMBER_OF_ATTRIBUTES = 3;
    private VkVertexInputAttributeDescription.Buffer viAttrs;
    private VkVertexInputBindingDescription.Buffer viBindings;

    public ImGuiVertexBufferStructure() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(0);

        // Texture coordinates
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(GraphConstants.FLOAT_LENGTH * 2);

        // Color
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .offset(GraphConstants.FLOAT_LENGTH * 4);

        viBindings.get(0)
                .binding(0)
                .stride(VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    @Override
    public void cleanup() {
        super.cleanup();
        viBindings.free();
        viAttrs.free();
    }
}
