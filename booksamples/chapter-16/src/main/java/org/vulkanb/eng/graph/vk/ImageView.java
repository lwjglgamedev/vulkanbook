package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class ImageView {

    private final int aspectMask;
    private final Device device;
    private final int mipLevels;
    private final long vkImageView;

    public ImageView(Device device, long vkImage, int format, int aspectMask, int mipLevels) {
        this(device, vkImage, format, aspectMask, mipLevels, VK_IMAGE_VIEW_TYPE_2D, 0, 1);

    }

    public ImageView(Device device, long vkImage, int format, int aspectMask, int mipLevels, int viewType,
                     int baseArrayLayer, int layerCount) {
        this.device = device;
        this.aspectMask = aspectMask;
        this.mipLevels = mipLevels;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);
            VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(vkImage)
                    .viewType(viewType)
                    .format(format)
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(baseArrayLayer)
                            .layerCount(layerCount));

            vkCheck(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                    "Failed to create image view");
            vkImageView = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroyImageView(device.getVkDevice(), vkImageView, null);
    }

    public int getAspectMask() {
        return aspectMask;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImageView() {
        return vkImageView;
    }
}
