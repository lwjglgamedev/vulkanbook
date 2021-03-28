package org.vulkanb.eng.graph.lighting;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.vulkanb.eng.graph.vk.VertexInputStateInfo;

import static org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;

public class EmptyVertexBufferStructure extends VertexInputStateInfo {

    public EmptyVertexBufferStructure() {
        vi = VkPipelineVertexInputStateCreateInfo.calloc();
        vi.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null);
    }
}