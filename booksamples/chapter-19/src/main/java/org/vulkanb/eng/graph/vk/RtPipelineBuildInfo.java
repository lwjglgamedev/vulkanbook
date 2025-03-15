package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkRayTracingShaderGroupCreateInfoKHR;

public class RtPipelineBuildInfo {

    private final ShaderModule[] shaderModules;
    private DescSetLayout[] descSetLayouts;
    private VkRayTracingShaderGroupCreateInfoKHR.Buffer groups;
    private PushConstRange[] pushConstRanges;

    public RtPipelineBuildInfo(ShaderModule[] shaderModules, VkRayTracingShaderGroupCreateInfoKHR.Buffer groups) {
        this.shaderModules = shaderModules;
        this.groups = groups;
    }

    public DescSetLayout[] getDescSetLayouts() {
        return descSetLayouts;
    }

    public VkRayTracingShaderGroupCreateInfoKHR.Buffer getGroups() {
        return groups;
    }

    public PushConstRange[] getPushConstRanges() {
        return pushConstRanges;
    }

    public ShaderModule[] getShaderModules() {
        return shaderModules;
    }

    public RtPipelineBuildInfo setDescSetLayouts(DescSetLayout[] descSetLayouts) {
        this.descSetLayouts = descSetLayouts;
        return this;
    }

    public RtPipelineBuildInfo setPushConstRanges(PushConstRange[] pushConstRanges) {
        this.pushConstRanges = pushConstRanges;
        return this;
    }
}
