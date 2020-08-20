package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK11.*;

public class VertexBufferStructure extends VertexInputStateInfo {

    public static final int TEXT_COORD_COMPONENTS = 2;
    private static final int NUMBER_OF_ATTRIBUTES = 2;
    private static final int POSITION_COMPONENTS = 3;
    private VkVertexInputAttributeDescription.Buffer viAttrs;
    private VkVertexInputBindingDescription.Buffer viBindings;

    public VertexBufferStructure() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

        // Texture coordinates
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH);

        viBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH +
                        TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH)
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
