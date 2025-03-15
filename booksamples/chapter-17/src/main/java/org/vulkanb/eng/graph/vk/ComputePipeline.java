package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class ComputePipeline {

    private final long vkPipeline;
    private final long vkPipelineLayout;

    public ComputePipeline(VkCtx vkCtx, CompPipelineBuildInfo buildInfo) {
        Logger.debug("Creating compute pipeline");
        Device device = vkCtx.getDevice();

        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.callocLong(1);
            ByteBuffer main = stack.UTF8("main");

            ShaderModule shaderModule = buildInfo.shaderModule();
            var shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(shaderModule.getShaderStage())
                    .module(shaderModule.getHandle())
                    .pName(main);
            if (shaderModule.getSpecInfo() != null) {
                shaderStage.pSpecializationInfo(shaderModule.getSpecInfo());
            }

            VkPushConstantRange.Buffer vpcr = null;
            if (buildInfo.pushConstantsSize() > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0)
                        .size(buildInfo.pushConstantsSize());
            }
            DescSetLayout[] descSetLayouts = buildInfo.descSetLayouts();
            int numLayouts = descSetLayouts != null ? descSetLayouts.length : 0;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, descSetLayouts[i].getVkDescLayout());
            }
            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);
            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);

            var computePipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(shaderStage)
                    .layout(vkPipelineLayout);
            vkCheck(vkCreateComputePipelines(device.getVkDevice(), vkCtx.getPipelineCache().getVkPipelineCache(),
                    computePipelineCreateInfo, null, lp), "Error creating compute pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying compute pipeline");
        vkDestroyPipelineLayout(vkCtx.getDevice().getVkDevice(), vkPipelineLayout, null);
        vkDestroyPipeline(vkCtx.getDevice().getVkDevice(), vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
}
