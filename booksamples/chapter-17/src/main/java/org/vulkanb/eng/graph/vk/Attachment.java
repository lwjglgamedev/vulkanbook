package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK11.*;

public class Attachment {

    private final boolean depthAttachment;
    private final Image image;
    private final ImageView imageView;

    public Attachment(Image image, ImageView imageView, boolean depthAttachment) {
        this.image = image;
        this.imageView = imageView;
        this.depthAttachment = depthAttachment;
    }

    public Attachment(Device device, int width, int height, int format, int usage) {
        Image.ImageData imageData = new Image.ImageData().width(width).height(height).
                usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(format);
        image = new Image(device, imageData);

        int aspectMask = calcAspectMask(usage);
        depthAttachment = aspectMask == VK_IMAGE_ASPECT_DEPTH_BIT;

        ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(image.getFormat()).aspectMask(aspectMask);
        imageView = new ImageView(device, image.getVkImage(), imageViewData);
    }

    public static int calcAspectMask(int usage) {
        int aspectMask = 0;
        if ((usage & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        }
        if ((usage & VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
        }
        return aspectMask;
    }

    public void cleanup() {
        imageView.cleanup();
        image.cleanup();
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
