package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public abstract class DescriptorSetLayout {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Device device;

    protected long vkDescriptorLayout;

    protected DescriptorSetLayout(Device device) {
        this.device = device;
    }

    public void cleanup() {
        LOGGER.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(device.getVkDevice(), vkDescriptorLayout, null);
    }

    public long getVkDescriptorLayout() {
        return vkDescriptorLayout;
    }


    public static class SamplerDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public SamplerDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, binding, stage);
        }
    }

    public static class SimpleDescriptorSetLayout extends DescriptorSetLayout {

        public SimpleDescriptorSetLayout(Device device, int descriptorType, int binding, int stage) {
            super(device);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
                layoutBindings.get(0)
                        .binding(binding)
                        .descriptorType(descriptorType)
                        .descriptorCount(1)
                        .stageFlags(stage);

                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                        .pBindings(layoutBindings);

                LongBuffer pSetLayout = stack.mallocLong(1);
                vkCheck(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, pSetLayout),
                        "Failed to create descriptor set layout");
                super.vkDescriptorLayout = pSetLayout.get(0);
            }
        }
    }

    public static class UniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public UniformDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, binding, stage);
        }
    }
}
