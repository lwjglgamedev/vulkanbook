package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class AttachmentsDescriptorSet extends DescriptorSet {

    private final int binding;
    private final Device device;
    private final TextureSampler textureSampler;

    public AttachmentsDescriptorSet(DescriptorPool descriptorPool, AttachmentsLayout descriptorSetLayout,
                                    List<Attachment> attachments, int binding) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            device = descriptorPool.getDevice();
            this.binding = binding;
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

            textureSampler = new TextureSampler(device, 1, false);

            update(attachments);
        }
    }

    public void cleanup() {
        textureSampler.cleanup();
    }

    @Override
    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }

    public void update(List<Attachment> attachments) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int numAttachments = attachments.size();
            VkWriteDescriptorSet.Buffer descrBuffer = VkWriteDescriptorSet.calloc(numAttachments, stack);
            for (int i = 0; i < numAttachments; i++) {
                Attachment attachment = attachments.get(i);
                VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .sampler(textureSampler.getVkSampler())
                        .imageView(attachment.getImageView().getVkImageView());
                if (attachment.isDepthAttachment()) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                }

                descrBuffer.get(i)
                        .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                        .dstSet(vkDescriptorSet)
                        .dstBinding(binding + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
            }

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}