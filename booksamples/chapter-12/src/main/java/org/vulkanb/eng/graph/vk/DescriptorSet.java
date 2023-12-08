package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public abstract class DescriptorSet {

    protected long vkDescriptorSet;

    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }

    public static class DynUniformDescriptorSet extends SimpleDescriptorSet {
        public DynUniformDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                       VulkanBuffer buffer, int binding, long size) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, size);
        }
    }

    public static class SimpleDescriptorSet extends DescriptorSet {

        public SimpleDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                   VulkanBuffer buffer, int binding, int type, long size) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Device device = descriptorPool.getDevice();
                LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
                pDescriptorSetLayout.put(0, descriptorSetLayout.getVkDescriptorLayout());
                VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                        .descriptorPool(descriptorPool.getVkDescriptorPool())
                        .pSetLayouts(pDescriptorSetLayout);

                LongBuffer pDescriptorSet = stack.mallocLong(1);
                vkCheck(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                        "Failed to create descriptor set");

                vkDescriptorSet = pDescriptorSet.get(0);

                VkDescriptorBufferInfo.Buffer bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffer.getBuffer())
                        .offset(0)
                        .range(size);

                VkWriteDescriptorSet.Buffer descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

                descrBuffer.get(0)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(vkDescriptorSet)
                        .dstBinding(binding)
                        .descriptorType(type)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);

                vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
            }
        }
    }

    public static class StorageDescriptorSet extends SimpleDescriptorSet {

        public StorageDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                    VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    buffer.getRequestedSize());
        }
    }

    public static class UniformDescriptorSet extends SimpleDescriptorSet {

        public UniformDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                    VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    buffer.getRequestedSize());
        }
    }
}
