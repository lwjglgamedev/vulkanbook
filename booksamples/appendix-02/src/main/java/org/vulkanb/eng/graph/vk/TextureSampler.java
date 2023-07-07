package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class TextureSampler {

    private static final int MAX_ANISOTROPY = 16;

    private final Device device;
    private final long vkSampler;

    public TextureSampler(Device device, int mipLevels, boolean anisotropyEnable) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .minLod(0.0f)
                    .maxLod(mipLevels)
                    .mipLodBias(0.0f);
            if (anisotropyEnable && device.isSamplerAnisotropy()) {
                samplerInfo
                        .anisotropyEnable(true)
                        .maxAnisotropy(MAX_ANISOTROPY);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp), "Failed to create sampler");
            vkSampler = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroySampler(device.getVkDevice(), vkSampler, null);
    }

    public long getVkSampler() {
        return vkSampler;
    }
}
