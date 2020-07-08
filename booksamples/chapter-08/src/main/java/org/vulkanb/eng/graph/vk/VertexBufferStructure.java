package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK10.*;

public class VertexBufferStructure {

    public static final int TEXT_COORD_COMPONENTS = 2;
    private static final int NUMBER_OF_ATTRIBUTES = 2;
    private static final int POSITION_COMPONENTS = 3;
    private VkPipelineVertexInputStateCreateInfo vi;
    private VkVertexInputAttributeDescription.Buffer viAttrs;
    private VkVertexInputBindingDescription.Buffer viBindings;

    public VertexBufferStructure() {
        this.viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        this.viBindings = VkVertexInputBindingDescription.calloc(1);
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

        // Texture coordinates
        i++;
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH);

        this.viBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH +
                        TEXT_COORD_COMPONENTS * GraphConstants.FLOAT_LENGTH)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        this.vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(this.viBindings)
                .pVertexAttributeDescriptions(this.viAttrs);
    }

    public void cleanUp() {
        this.vi.free();
        this.viBindings.free();
        this.viAttrs.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
