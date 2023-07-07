package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Image {

    private final Device device;
    private final int format;
    private final int mipLevels;
    private final long vkImage;
    private final long vkMemory;

    public Image(Device device, ImageData imageData) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.format = imageData.format;
            this.mipLevels = imageData.mipLevels;

            VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .extent(it -> it
                            .width(imageData.width)
                            .height(imageData.height)
                            .depth(1)
                    )
                    .mipLevels(mipLevels)
                    .arrayLayers(imageData.arrayLayers)
                    .samples(imageData.sampleCount)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(imageData.usage);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateImage(device.getVkDevice(), imageCreateInfo, null, lp), "Failed to create image");
            vkImage = lp.get(0);

            // Get memory requirements for this object
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device.getVkDevice(), vkImage, memReqs);

            // Select memory size and type
            VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                            memReqs.memoryTypeBits(), 0));

            // Allocate memory
            vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
            vkMemory = lp.get(0);

            // Bind memory
            vkCheck(vkBindImageMemory(device.getVkDevice(), vkImage, vkMemory, 0),
                    "Failed to bind image memory");
        }
    }

    public void cleanup() {
        vkDestroyImage(device.getVkDevice(), vkImage, null);
        vkFreeMemory(device.getVkDevice(), vkMemory, null);
    }

    public int getFormat() {
        return format;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImage() {
        return vkImage;
    }

    public long getVkMemory() {
        return vkMemory;
    }

    public static class ImageData {
        private int arrayLayers;
        private int format;
        private int height;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;

        public ImageData() {
            this.format = VK_FORMAT_R8G8B8A8_SRGB;
            this.mipLevels = 1;
            this.sampleCount = 1;
            this.arrayLayers = 1;
        }

        public ImageData arrayLayers(int arrayLayers) {
            this.arrayLayers = arrayLayers;
            return this;
        }

        public ImageData format(int format) {
            this.format = format;
            return this;
        }

        public ImageData height(int height) {
            this.height = height;
            return this;
        }

        public ImageData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageData sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }

        public ImageData usage(int usage) {
            this.usage = usage;
            return this;
        }

        public ImageData width(int width) {
            this.width = width;
            return this;
        }
    }
}