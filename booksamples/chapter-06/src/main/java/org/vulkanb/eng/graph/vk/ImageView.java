package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class ImageView {

    private final int aspectMask;
    private final int mipLevels;
    private final long vkImage;
    private final long vkImageView;

    public ImageView(Device device, long vkImage, ImageViewData imageViewData) {
        this.aspectMask = imageViewData.aspectMask;
        this.mipLevels = imageViewData.mipLevels;
        this.vkImage = vkImage;
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);
            var viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(imageViewData.viewType)
                    .format(imageViewData.format)
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(imageViewData.baseArrayLayer)
                            .layerCount(imageViewData.layerCount));

            vkCheck(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                    "Failed to create image view");
            vkImageView = lp.get(0);
        }
    }

    public void cleanup(Device device) {
        vkDestroyImageView(device.getVkDevice(), vkImageView, null);
    }

    public int getAspectMask() {
        return aspectMask;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImage() {
        return vkImage;
    }

    public long getVkImageView() {
        return vkImageView;
    }

    public static class ImageViewData {
        private int aspectMask;
        private int baseArrayLayer;
        private int format;
        private int layerCount;
        private int mipLevels;
        private int viewType;

        public ImageViewData() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK_IMAGE_VIEW_TYPE_2D;
        }

        public ImageView.ImageViewData aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public ImageView.ImageViewData baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public ImageView.ImageViewData format(int format) {
            this.format = format;
            return this;
        }

        public ImageView.ImageViewData layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public ImageView.ImageViewData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageView.ImageViewData viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }
    }
}
