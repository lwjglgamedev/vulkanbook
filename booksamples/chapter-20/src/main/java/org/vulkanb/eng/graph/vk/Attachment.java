package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK13.*;

public class Attachment {

    private final Image image;
    private final ImageView imageView;

    private boolean depthAttachment;

    public Attachment(VkCtx vkCtx, int width, int height, int format, int usage, int layers) {
        var imageData = new Image.ImageData().width(width).height(height).
                format(format);

        int aspectMask = 0;
        if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            usage = usage | VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            depthAttachment = false;
        }
        if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            usage = usage | VK_IMAGE_USAGE_SAMPLED_BIT;
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
            depthAttachment = true;
        }
        if ((usage & VK_IMAGE_USAGE_STORAGE_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            usage = usage | VK_IMAGE_USAGE_SAMPLED_BIT;
            depthAttachment = false;
        }
        imageData.usage(usage);

        if (layers > 0) {
            imageData.arrayLayers(layers);
        }
        image = new Image(vkCtx, imageData);

        var imageViewData = new ImageView.ImageViewData().format(image.getFormat()).aspectMask(aspectMask)
                .usage(usage);
        if (layers > 1) {
            imageViewData.viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY);
            imageViewData.layerCount(layers);
        }
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
