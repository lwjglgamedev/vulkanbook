package org.vulkanb.eng.graph.geometry;

import org.vulkanb.eng.graph.vk.*;

import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryAttachments {

    private static final int NUMBER_ATTACHMENTS = 4;
    public static final int NUMBER_COLOR_ATTACHMENTS = NUMBER_ATTACHMENTS - 1;

    private final List<Attachment> attachments;
    private final Attachment deptAttachment;
    private final int height;
    private final int width;

    public GeometryAttachments(Device device, int width, int height) {
        this.width = width;
        this.height = height;
        attachments = new ArrayList<>();

        // Albedo attachment
        Attachment attachment = new Attachment(device, width, height,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments.add(attachment);

        // Normals attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_A2B10G10R10_UNORM_PACK32, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments.add(attachment);

        // PBR attachment
        attachment = new Attachment(device, width, height,
                VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        attachments.add(attachment);

        // Depth attachment
        deptAttachment = new Attachment(device, width, height,
                VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        attachments.add(deptAttachment);
    }

    public void cleanup() {
        attachments.forEach(Attachment::cleanup);
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public Attachment getDepthAttachment() {
        return deptAttachment;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }
}
