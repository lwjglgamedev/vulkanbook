package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class DescSetLayout {

    private final LayoutInfo[] layoutInfos;
    protected long vkDescLayout;

    public DescSetLayout(VkCtx vkCtx, LayoutInfo layoutInfo) {
        this(vkCtx, new DescSetLayout.LayoutInfo[]{layoutInfo});
    }

    public DescSetLayout(VkCtx vkCtx, LayoutInfo[] layoutInfos) {
        this.layoutInfos = layoutInfos;
        try (var stack = MemoryStack.stackPush()) {
            int count = layoutInfos.length;
            var layoutBindings = VkDescriptorSetLayoutBinding.calloc(count, stack);
            for (int i = 0; i < count; i++) {
                LayoutInfo layoutInfo = layoutInfos[i];
                layoutBindings.get(i)
                        .binding(layoutInfo.binding())
                        .descriptorType(layoutInfo.descType())
                        .descriptorCount(layoutInfo.descCount())
                        .stageFlags(layoutInfo.stage());
            }

            var vkLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(layoutBindings);

            LongBuffer pSetLayout = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(vkCtx.getDevice().getVkDevice(), vkLayoutInfo, null, pSetLayout),
                    "Failed to create descriptor set layout");
            vkDescLayout = pSetLayout.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(vkCtx.getDevice().getVkDevice(), vkDescLayout, null);
    }

    public LayoutInfo getLayoutInfo() {
        return getLayoutInfos()[0];
    }

    public LayoutInfo[] getLayoutInfos() {
        return layoutInfos;
    }

    public long getVkDescLayout() {
        return vkDescLayout;
    }

    public record LayoutInfo(int descType, int binding, int descCount, int stage) {
    }
}
