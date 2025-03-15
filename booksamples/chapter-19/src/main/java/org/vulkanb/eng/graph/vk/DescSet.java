package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class DescSet {

    protected long vkDescriptorSet;

    public DescSet(Device device, DescPool descPool, DescSetLayout descSetLayout) {
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, descSetLayout.getVkDescLayout());
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descPool.getVkDescPool())
                    .pSetLayouts(pDescriptorSetLayout);

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            vkCheck(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");

            vkDescriptorSet = pDescriptorSet.get(0);
        }
    }

    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }

    public void setBuffer(Device device, VkBuffer buffer, long range, int binding, int type) {
        try (var stack = MemoryStack.stackPush()) {
            var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.getBuffer())
                    .offset(0)
                    .range(range);

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }

    public void setImage(Device device, ImageView imageView, TextureSampler textureSampler, int binding) {
        List<ImageView> imageViews = new ArrayList<>();
        imageViews.add(imageView);
        setImages(device, imageViews, textureSampler, binding);
    }

    public void setImages(Device device, List<ImageView> imgViews, TextureSampler textureSampler, int baseBinding) {
        try (var stack = MemoryStack.stackPush()) {
            int numImages = imgViews.size();
            var descrBuffer = VkWriteDescriptorSet.calloc(numImages, stack);
            for (int i = 0; i < numImages; i++) {
                ImageView iv = imgViews.get(i);
                var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageView(iv.getVkImageView());
                if (textureSampler != null) {
                    imageInfo.sampler(textureSampler.getVkSampler());
                }

                if (iv.isDepthImage()) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                } else if ((iv.getUsage() & VK_IMAGE_USAGE_STORAGE_BIT) > 0) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_GENERAL);
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                }

                descrBuffer.get(i)
                        .sType$Default()
                        .dstSet(vkDescriptorSet)
                        .dstBinding(baseBinding + i)
                        .descriptorType(textureSampler == null ?
                                VK_DESCRIPTOR_TYPE_STORAGE_IMAGE : VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(imageInfo);
            }

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }

    public void setImagesArr(Device device, List<ImageView> imgViews, TextureSampler textureSampler, int baseBinding) {
        try (var stack = MemoryStack.stackPush()) {
            int numImages = imgViews.size();
            VkDescriptorImageInfo.Buffer imageInfos = VkDescriptorImageInfo.calloc(numImages, stack);
            for (int i = 0; i < numImages; i++) {
                ImageView iv = imgViews.get(i);
                VkDescriptorImageInfo imageInfo = imageInfos.get(i);
                imageInfo.imageView(iv.getVkImageView())
                        .sampler(textureSampler.getVkSampler());

                if (iv.isDepthImage()) {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                } else {
                    imageInfo.imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                }
            }

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);
            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(baseBinding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(numImages)
                    .pImageInfo(imageInfos);
            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }

    public void setTLAS(Device device, int binding, int type, TLAS tlas) {
        try (var stack = MemoryStack.stackPush()) {
            var accelSt = VkWriteDescriptorSetAccelerationStructureKHR
                    .calloc(stack)
                    .sType$Default()
                    .pAccelerationStructures(stack.longs(tlas.getHandle()));

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pNext(accelSt);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }
}
