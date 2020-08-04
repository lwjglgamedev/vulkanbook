package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class MaterialDescriptorSet extends DescriptorSet {

    public MaterialDescriptorSet(DescriptorPool descriptorPool, MaterialDescriptorSetLayout descriptorSetLayout,
                                 VulkanBuffer materialBuffer, int binding) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = descriptorPool.getDevice();
            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, descriptorSetLayout.getVkDescriptorLayout());
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool.getVkDescriptorPool())
                    .pSetLayouts(pDescriptorSetLayout);

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            vkCheck(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");

            vkDescriptorSet = pDescriptorSet.get(0);

            VkDescriptorBufferInfo.Buffer matBufferInfo = VkDescriptorBufferInfo.callocStack(1, stack)
                    .buffer(materialBuffer.getBuffer())
                    .offset(0)
                    .range(descriptorSetLayout.getMaterialSize());

            VkWriteDescriptorSet.Buffer descrBuffer = VkWriteDescriptorSet.callocStack(1, stack);

            // Material uniform
            descrBuffer.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .descriptorCount(1)
                    .pBufferInfo(matBufferInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}
