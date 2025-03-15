package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class TextureSampler {

    private static final int MAX_ANISOTROPY = 16;

    private final long vkSampler;

    public TextureSampler(VkCtx vkCtx, TextureSamplerInfo textureSamplerInfo) {
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .addressModeU(textureSamplerInfo.addressMode())
                    .addressModeV(textureSamplerInfo.addressMode())
                    .addressModeW(textureSamplerInfo.addressMode())
                    .borderColor(textureSamplerInfo.borderColor())
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_NEVER)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .minLod(0.0f)
                    .maxLod(textureSamplerInfo.mipLevels())
                    .mipLodBias(0.0f);
            if (textureSamplerInfo.anisotropy() && vkCtx.getDevice().isSamplerAnisotropy()) {
                samplerInfo
                        .anisotropyEnable(true)
                        .maxAnisotropy(MAX_ANISOTROPY);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSampler(vkCtx.getDevice().getVkDevice(), samplerInfo, null, lp), "Failed to create sampler");
            vkSampler = lp.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vkDestroySampler(vkCtx.getDevice().getVkDevice(), vkSampler, null);
    }

    public long getVkSampler() {
        return vkSampler;
    }
}
