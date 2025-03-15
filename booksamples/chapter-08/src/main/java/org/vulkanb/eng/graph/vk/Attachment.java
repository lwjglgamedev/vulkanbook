package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK13.*;

public class Attachment {

    private final Image image;
    private final ImageView imageView;
    private boolean depthAttachment;

    public Attachment(VkCtx vkCtx, int width, int height, int format, int usage) {
        var imageData = new Image.ImageData().width(width).height(height).usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(format);
        image = new Image(vkCtx, imageData);

        int aspectMask = 0;
        if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            depthAttachment = false;
        }
        if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
            depthAttachment = true;
        }

        var imageViewData = new ImageView.ImageViewData().format(image.getFormat()).aspectMask(aspectMask);
        imageView = new ImageView(vkCtx.getDevice(), image.getVkImage(), imageViewData, depthAttachment);
    }

    public void cleanup(VkCtx vkCtx) {
        imageView.cleanup(vkCtx.getDevice());
        image.cleanup(vkCtx);
    }

    public Image getImage() {
        return image;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public boolean isDepthAttachment() {
        return depthAttachment;
    }
}
