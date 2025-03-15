package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

import static org.lwjgl.vulkan.VK13.VK_FORMAT_UNDEFINED;

public class PipelineBuildInfo {

    private final int colorFormat;
    private final ShaderModule[] shaderModules;
    private final VkPipelineVertexInputStateCreateInfo vi;
    private int depthFormat;
    private DescSetLayout[] descSetLayouts;
    private PushConstRange[] pushConstRanges;

    public PipelineBuildInfo(ShaderModule[] shaderModules, VkPipelineVertexInputStateCreateInfo vi, int colorFormat) {
        this.shaderModules = shaderModules;
        this.vi = vi;
        this.colorFormat = colorFormat;
        depthFormat = VK_FORMAT_UNDEFINED;
    }

    public int getColorFormat() {
        return colorFormat;
    }

    public int getDepthFormat() {
        return depthFormat;
    }

    public DescSetLayout[] getDescSetLayouts() {
        return descSetLayouts;
    }

    public PushConstRange[] getPushConstRanges() {
        return pushConstRanges;
    }

    public ShaderModule[] getShaderModules() {
        return shaderModules;
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }

    public PipelineBuildInfo setDepthFormat(int depthFormat) {
        this.depthFormat = depthFormat;
        return this;
    }

    public PipelineBuildInfo setDescSetLayouts(DescSetLayout[] descSetLayouts) {
        this.descSetLayouts = descSetLayouts;
        return this;
    }

    public PipelineBuildInfo setPushConstRanges(PushConstRange[] pushConstRanges) {
        this.pushConstRanges = pushConstRanges;
        return this;
    }
}