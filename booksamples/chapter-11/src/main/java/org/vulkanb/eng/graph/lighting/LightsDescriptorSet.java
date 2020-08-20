package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class LightsDescriptorSet extends DescriptorSet {

    public LightsDescriptorSet(DescriptorPool descriptorPool, LightsDescriptorSetLayout lightsDescriptorSetLayout,
                               VulkanBuffer lightsBuffer, VulkanBuffer ambientLightBuffer, int binding) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = descriptorPool.getDevice();
            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, lightsDescriptorSetLayout.getVkDescriptorLayout());
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool.getVkDescriptorPool())
                    .pSetLayouts(pDescriptorSetLayout);

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            vkCheck(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");

            vkDescriptorSet = pDescriptorSet.get(0);

            VkDescriptorBufferInfo.Buffer descrLights = VkDescriptorBufferInfo.callocStack(1, stack)
                    .buffer(lightsBuffer.getBuffer())
                    .offset(0)
                    .range(lightsBuffer.getRequestedSize());

            VkDescriptorBufferInfo.Buffer descrAmbientLight = VkDescriptorBufferInfo.callocStack(1, stack)
                    .buffer(ambientLightBuffer.getBuffer())
                    .offset(0)
                    .range(ambientLightBuffer.getRequestedSize());

            VkWriteDescriptorSet.Buffer descrBuffer = VkWriteDescriptorSet.callocStack(2, stack);

            int i = 0;
            descrBuffer.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding + i)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(descrLights);
            i++;

            // Ambient Light
            descrBuffer.get(i)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding + i)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .pBufferInfo(descrAmbientLight);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}