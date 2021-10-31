package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public abstract class DescriptorSetLayout {
    private final Device device;

    protected long vkDescriptorLayout;

    protected DescriptorSetLayout(Device device) {
        this.device = device;
    }

    public void cleanup() {
        Logger.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(device.getVkDevice(), vkDescriptorLayout, null);
    }

    public long getVkDescriptorLayout() {
        return vkDescriptorLayout;
    }

    public static class DynUniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public DynUniformDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1, binding, stage);
        }
    }

    public static class SamplerDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public SamplerDescriptorSetLayout(Device device, int descriptorCount, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, descriptorCount, binding, stage);
        }
    }

    public static class SimpleDescriptorSetLayout extends DescriptorSetLayout {

        public SimpleDescriptorSetLayout(Device device, int descriptorType, int descriptorCount, int binding, int stage) {
            super(device);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                layoutBindings.get(0)
                        .binding(binding)
                        .descriptorType(descriptorType)
                        .descriptorCount(descriptorCount)
                        .stageFlags(stage);

                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                        .pBindings(layoutBindings);

                LongBuffer pSetLayout = stack.mallocLong(1);
                vkCheck(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, pSetLayout),
                        "Failed to create descriptor set layout");
                super.vkDescriptorLayout = pSetLayout.get(0);
            }
        }
    }

    public static class StorageDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public StorageDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1, binding, stage);
        }
    }

    public static class UniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public UniformDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, binding, stage);
        }
    }
}
