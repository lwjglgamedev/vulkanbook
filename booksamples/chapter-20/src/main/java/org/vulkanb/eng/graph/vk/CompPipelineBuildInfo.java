package org.vulkanb.eng.graph.vk;

public record CompPipelineBuildInfo(ShaderModule shaderModule, DescSetLayout[] descSetLayouts, int pushConstantsSize) {
}
