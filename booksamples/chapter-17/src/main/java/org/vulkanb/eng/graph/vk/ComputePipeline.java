package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class ComputePipeline {

    private final Device device;
    private final long vkPipeline;
    private final long vkPipelineLayout;

    public ComputePipeline(PipelineCache pipelineCache, ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        Logger.debug("Creating compute pipeline");
        device = pipelineCache.getDevice();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.callocLong(1);
            ByteBuffer main = stack.UTF8("main");

            ShaderProgram.ShaderModule[] shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            int numModules = shaderModules != null ? shaderModules.length : 0;
            if (numModules != 1) {
                throw new RuntimeException("Compute pipelines can have only one shader");
            }
            ShaderProgram.ShaderModule shaderModule = shaderModules[0];
            VkPipelineShaderStageCreateInfo shaderStage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(shaderModule.shaderStage())
                    .module(shaderModule.handle())
                    .pName(main);
            if (shaderModule.specInfo() != null) {
                shaderStage.pSpecializationInfo(shaderModule.specInfo());
            }

            VkPushConstantRange.Buffer vpcr = null;
            if (pipeLineCreationInfo.pushConstantsSize() > 0) {
                vpcr = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
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

            VkComputePipelineCreateInfo.Buffer computePipelineCreateInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .stage(shaderStage)
                    .layout(vkPipelineLayout);
            vkCheck(vkCreateComputePipelines(device.getVkDevice(), pipelineCache.getVkPipelineCache(), computePipelineCreateInfo,
                    null, lp), "Error creating compute pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public void cleanup() {
        Logger.debug("Destroying compute pipeline");
        vkDestroyPipelineLayout(device.getVkDevice(), vkPipelineLayout, null);
        vkDestroyPipeline(device.getVkDevice(), vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }

    public record PipeLineCreationInfo(ShaderProgram shaderProgram, DescriptorSetLayout[] descriptorSetLayouts,
                                       int pushConstantsSize) {
    }
}
