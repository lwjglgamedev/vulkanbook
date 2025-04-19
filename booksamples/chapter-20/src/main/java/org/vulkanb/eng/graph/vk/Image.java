package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class Image {

    private final long allocation;
    private final int format;
    private final int height;
    private final int mipLevels;
    private final long vkImage;
    private final int width;

    public Image(VkCtx vkCtx, ImageData imageData) {
        try (var stack = MemoryStack.stackPush()) {
            this.format = imageData.format;
            this.mipLevels = imageData.mipLevels;
            this.width = imageData.width;
            this.height = imageData.height;

            VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
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

            var allocCreateInfo = VmaAllocationCreateInfo.calloc(1, stack)
                    .get(0)
                    .usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(imageData.memUsage)
                    .priority(1.0f);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateImage(vkCtx.getMemAlloc().getVmaAlloc(), imageCreateInfo, allocCreateInfo, lp, pAllocation, null),
                    "Failed to create image");
            vkImage = lp.get(0);
            allocation = pAllocation.get(0);
        }
    }

    public void cleanup(VkCtx vkCtx) {
        vmaDestroyImage(vkCtx.getMemAlloc().getVmaAlloc(), vkImage, allocation);
    }

    public int getFormat() {
        return format;
    }

    public int getHeight() {
        return height;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImage() {
        return vkImage;
    }

    public int getWidth() {
        return width;
    }

    public static class ImageData {
        private int arrayLayers;
        private int format;
        private int height;
        private int memUsage;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;

        public ImageData() {
            format = VK_FORMAT_R8G8B8A8_SRGB;
            mipLevels = 1;
            sampleCount = 1;
            arrayLayers = 1;
            memUsage = VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT;
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

        public ImageData memUsage(int memUsage) {
            this.memUsage = memUsage;
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
