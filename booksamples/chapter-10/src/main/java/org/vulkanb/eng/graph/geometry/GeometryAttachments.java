package org.vulkanb.eng.graph.geometry;

import org.vulkanb.eng.graph.vk.*;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryAttachments {

    public static final int NUMBER_ATTACHMENTS = 2;
    public static final int NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1;
    private Attachment[] attachments;
    private int depthAttachmentPos;
    private int height;
    private int width;

    public GeometryAttachments(Device device, int width, int height) {
        this.width = width;
        this.height = height;
        attachments = new Attachment[NUMBER_ATTACHMENTS];

        int i = 0;
        // Albedo attachment
        Attachment attachment = new Attachment(device, width, height,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments[i] = attachment;
        i++;

        // Depth attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        attachments[i] = attachment;
        depthAttachmentPos = i;
    }

    public void cleanup() {
        Arrays.stream(attachments).forEach(Attachment::cleanup);
    }

    public Attachment[] getAttachments() {
        return attachments;
    }

    public Attachment getDepthAttachment() {
        return attachments[depthAttachmentPos];
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }
}
