package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class InputDescriptorSet extends DescriptorSet {

    public InputDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                              Attachment[] attachments, int binding) {
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

            int numAttachments = attachments.length;
            VkWriteDescriptorSet.Buffer descrBuffer = VkWriteDescriptorSet.callocStack(numAttachments, stack);
            for (int i = 0; i < numAttachments; i++) {
                Attachment attachment = attachments[i];
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.callocStack(1, stack)
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .imageView(attachment.getImageView().getVkImageView());

                descrBuffer.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(vkDescriptorSet)
                        .dstBinding(binding + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
            }

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}
