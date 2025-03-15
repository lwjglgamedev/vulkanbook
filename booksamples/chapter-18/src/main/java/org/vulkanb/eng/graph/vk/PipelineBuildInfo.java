package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

import static org.lwjgl.vulkan.VK13.VK_FORMAT_UNDEFINED;

public class PipelineBuildInfo {

    private final int[] colorFormats;
    private final ShaderModule[] shaderModules;
    private final VkPipelineVertexInputStateCreateInfo vi;
    private boolean depthClamp;
    private int depthFormat;
    private DescSetLayout[] descSetLayouts;
    private PushConstRange[] pushConstRanges;
    private boolean useBlend;

    public PipelineBuildInfo(ShaderModule[] shaderModules, VkPipelineVertexInputStateCreateInfo vi, int[] colorFormats) {
        this.shaderModules = shaderModules;
        this.vi = vi;
        this.colorFormats = colorFormats;
        depthFormat = VK_FORMAT_UNDEFINED;
        useBlend = false;
        depthClamp = false;
    }

    public int[] getColorFormats() {
        return colorFormats;
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

    public boolean isDepthClamp() {
        return depthClamp;
    }

    public boolean isUseBlend() {
        return useBlend;
    }

    public PipelineBuildInfo setDepthClamp(boolean depthClamp) {
        this.depthClamp = depthClamp;
        return this;
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

    public PipelineBuildInfo setUseBlend(boolean useBlend) {
        this.useBlend = useBlend;
        return this;
    }
}