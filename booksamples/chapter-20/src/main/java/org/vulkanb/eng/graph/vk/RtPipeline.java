package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCreateRayTracingPipelinesKHR;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class RtPipeline {

    private final long vkPipeline;
    private final long vkPipelineLayout;

    public RtPipeline(VkCtx vkCtx, RtPipelineBuildInfo buildInfo) {
        Logger.debug("Creating RT pipeline");
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
                if (shaderModule.getSpecInfo() != null) {
                    shaderStages.get(i).pSpecializationInfo(shaderModule.getSpecInfo());
                }
            }

            VkPushConstantRange.Buffer vpcr = null;
            PushConstRange[] pushConstRanges = buildInfo.getPushConstRanges();
            int numPushConstants = pushConstRanges != null ? pushConstRanges.length : 0;
            if (numPushConstants > 0) {
                vpcr = VkPushConstantRange.calloc(numPushConstants, stack);
                for (int i = 0; i < numPushConstants; i++) {
                    PushConstRange pushConstRange = pushConstRanges[i];
                    vpcr.get(i)
                            .stageFlags(pushConstRange.stage())
                            .offset(pushConstRange.offset())
                            .size(pushConstRange.size());
                }
            }

            DescSetLayout[] descSetLayouts = buildInfo.getDescSetLayouts();
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
                    "Error creating ray tracing pipeline layout");
            vkPipelineLayout = lp.get(0);

            vkCheck(vkCreateRayTracingPipelinesKHR(device.getVkDevice(), VK_NULL_HANDLE, VK_NULL_HANDLE,
                            VkRayTracingPipelineCreateInfoKHR
                                    .calloc(1, stack)
                                    .sType$Default()
                                    .pStages(shaderStages)
                                    .maxPipelineRayRecursionDepth(1)
                                    .pGroups(buildInfo.getGroups())
                                    .layout(vkPipelineLayout), null, lp),
                    "Error creating ray tracing pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying RT pipeline");
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
