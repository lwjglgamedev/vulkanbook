package org.vulkanb.eng.graph.geometry;

import org.vulkanb.eng.graph.vk.*;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;

public class GeometryAttachments {

    public static final int NUMBER_ATTACHMENTS = 4;
    public static final int NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1;
    private Attachment[] attachments;

    public GeometryAttachments(Device device, int width, int height) {
        attachments = new Attachment[NUMBER_ATTACHMENTS];

        int i = 0;
        // Albedo attachment
        Attachment attachment = new Attachment(device, width, height,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments[i] = attachment;
        i++;

        // Normals attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments[i] = attachment;
        i++;

        // PBR attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments[i] = attachment;
        i++;

        // Depth attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        attachments[i] = attachment;
    }

    public void cleanup() {
        Arrays.stream(attachments).forEach(Attachment::cleanup);
    }

    public Attachment getAlbedoAttachment() {
        return attachments[0];
    }

    public Attachment[] getAttachments() {
        return attachments;
    }

    public Attachment getDepthAttachment() {
        return attachments[3];
    }

    public Attachment getNormalsAttachment() {
        return attachments[1];
    }

    public Attachment getPBRAttachment() {
        return this.attachments[2];
    }

}
