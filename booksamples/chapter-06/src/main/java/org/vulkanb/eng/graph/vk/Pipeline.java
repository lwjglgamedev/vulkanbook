package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class Pipeline {

    private final long vkPipeline;
    private final long vkPipelineLayout;

    public Pipeline(VkCtx vkCtx, PipelineBuildInfo buildInfo) {
        Logger.debug("Creating pipeline");
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);

            ByteBuffer main = stack.UTF8("main");

            ShaderModule[] shaderModules = buildInfo.getShaderModules();
            int numModules = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModule.getShaderStage())
                        .module(shaderModule.getHandle())
                        .pName(main);
            }

            var assemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            var viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .lineWidth(1.0f);

            var multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            var dynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(
                            VK_DYNAMIC_STATE_VIEWPORT,
                            VK_DYNAMIC_STATE_SCISSOR
                    ));

            var blendAttState = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(false);
            var colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(blendAttState);

            IntBuffer colorFormats = stack.mallocInt(1);
            colorFormats.put(0, buildInfo.getColorFormat());
            var rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(1)
                    .pColorAttachmentFormats(colorFormats);

            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default();

            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);

            var createInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .renderPass(VK_NULL_HANDLE)
                    .pStages(shaderStages)
                    .pVertexInputState(buildInfo.getVi())
                    .pInputAssemblyState(assemblyStateCreateInfo)
                    .pViewportState(viewportStateCreateInfo)
                    .pRasterizationState(rasterizationStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pMultisampleState(multisampleStateCreateInfo)
                    .pDynamicState(dynamicStateCreateInfo)
                    .layout(vkPipelineLayout)
                    .pNext(rendCreateInfo);
            vkCheck(vkCreateGraphicsPipelines(device.getVkDevice(), vkCtx.getPipelineCache().getVkPipelineCache(), createInfo, null, lp),
                    "Error creating graphics pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying pipeline");
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();
        vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null);
        vkDestroyPipeline(vkDevice, vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
}
