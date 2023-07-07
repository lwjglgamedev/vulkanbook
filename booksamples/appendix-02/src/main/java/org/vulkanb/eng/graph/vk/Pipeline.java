package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Pipeline {

    private final Device device;
    private final long vkPipeline;
    private final long vkPipelineLayout;

    public Pipeline(PipelineCache pipelineCache, Pipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        Logger.debug("Creating pipeline");
        device = pipelineCache.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);

            ByteBuffer main = stack.UTF8("main");

            ShaderProgram.ShaderModule[] shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            int numModules = shaderModules.length;
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderProgram.ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(shaderModule.shaderStage())
                        .module(shaderModule.handle())
                        .pName(main);
                if (shaderModule.specInfo() != null) {
                    shaderStages.get(i).pSpecializationInfo(shaderModule.specInfo());
                }
            }

            VkPipelineInputAssemblyStateCreateInfo vkPipelineInputAssemblyStateCreateInfo =
                    VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkPipelineViewportStateCreateInfo vkPipelineViewportStateCreateInfo =
                    VkPipelineViewportStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                            .viewportCount(1)
                            .scissorCount(1);

            VkPipelineRasterizationStateCreateInfo vkPipelineRasterizationStateCreateInfo =
                    VkPipelineRasterizationStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                            .polygonMode(VK_POLYGON_MODE_FILL)
                            .cullMode(VK_CULL_MODE_NONE)
                            .frontFace(VK_FRONT_FACE_CLOCKWISE)
                            .lineWidth(1.0f);

            VkPipelineMultisampleStateCreateInfo vkPipelineMultisampleStateCreateInfo =
                    VkPipelineMultisampleStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                            .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo ds = null;
            if (pipeLineCreationInfo.hasDepthAttachment()) {
                ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                        .depthTestEnable(true)
                        .depthWriteEnable(true)
                        .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);
            }

            VkPipelineColorBlendAttachmentState.Buffer blendAttState = VkPipelineColorBlendAttachmentState.calloc(
                    pipeLineCreationInfo.numColorAttachments(), stack);
            for (int i = 0; i < pipeLineCreationInfo.numColorAttachments(); i++) {
                blendAttState.get(i)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(pipeLineCreationInfo.useBlend());
                if (pipeLineCreationInfo.useBlend()) {
                    blendAttState.get(i).colorBlendOp(VK_BLEND_OP_ADD)
                            .alphaBlendOp(VK_BLEND_OP_ADD)
                            .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                            .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                            .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                            .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
                }
            }
            VkPipelineColorBlendStateCreateInfo colorBlendState =
                    VkPipelineColorBlendStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                            .pAttachments(blendAttState);

            VkPipelineDynamicStateCreateInfo vkPipelineDynamicStateCreateInfo =
                    VkPipelineDynamicStateCreateInfo.calloc(stack)
                            .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                            .pDynamicStates(stack.ints(
                                    VK_DYNAMIC_STATE_VIEWPORT,
                                    VK_DYNAMIC_STATE_SCISSOR
                            ));

            VkPushConstantRange.Buffer vpcr = null;
            if (pipeLineCreationInfo.pushConstantsSize() > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                        .offset(0)
                        .size(pipeLineCreationInfo.pushConstantsSize());
            }

            DescriptorSetLayout[] descriptorSetLayouts = pipeLineCreationInfo.descriptorSetLayouts();
            int numLayouts = descriptorSetLayouts != null ? descriptorSetLayouts.length : 0;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, descriptorSetLayouts[i].getVkDescriptorLayout());
            }

            VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);

            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(pipeLineCreationInfo.viInputStateInfo().getVi())
                    .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                    .pViewportState(vkPipelineViewportStateCreateInfo)
                    .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                    .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(vkPipelineDynamicStateCreateInfo)
                    .layout(vkPipelineLayout)
                    .renderPass(pipeLineCreationInfo.vkRenderPass);
            if (ds != null) {
                pipeline.pDepthStencilState(ds);
            }
            vkCheck(vkCreateGraphicsPipelines(device.getVkDevice(), pipelineCache.getVkPipelineCache(), pipeline, null, lp),
                    "Error creating graphics pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public void cleanup() {
        Logger.debug("Destroying pipeline");
        vkDestroyPipelineLayout(device.getVkDevice(), vkPipelineLayout, null);
        vkDestroyPipeline(device.getVkDevice(), vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }

    public record PipeLineCreationInfo(long vkRenderPass, ShaderProgram shaderProgram, int numColorAttachments,
                                       boolean hasDepthAttachment, boolean useBlend,
                                       int pushConstantsSize, VertexInputStateInfo viInputStateInfo,
                                       DescriptorSetLayout[] descriptorSetLayouts) {
        public void cleanup() {
            viInputStateInfo.cleanup();
        }
    }
}
