package org.vulkanb.eng.graph.scn;

import org.lwjgl.vulkan.VkExtent2D;
import org.vulkanb.eng.graph.vk.*;

import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class MrtAttachments {

    public static final int ALBEDO_FORMAT = VK_FORMAT_R8G8B8A8_UNORM;
    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
    public static final int NORMAL_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    public static final int PBR_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    public static final int POSITION_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;

    private final List<Attachment> colorAttachments;
    private final Attachment deptAttachment;
    private final int height;
    private final int width;

    public MrtAttachments(VkCtx vkCtx) {
        VkExtent2D extent2D = vkCtx.getSwapChain().getSwapChainExtent();
        width = extent2D.width();
        height = extent2D.height();
        colorAttachments = new ArrayList<>();

        // Position attachment
        var attachment = new Attachment(vkCtx, width, height, POSITION_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        colorAttachments.add(attachment);

        // Albedo attachment
        attachment = new Attachment(vkCtx, width, height, ALBEDO_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        colorAttachments.add(attachment);

        // Normals attachment
        attachment = new Attachment(vkCtx, width, height,
                NORMAL_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        colorAttachments.add(attachment);

        // PBR attachment
        attachment = new Attachment(vkCtx, width, height,
                PBR_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
        colorAttachments.add(attachment);

        // Depth attachment
        deptAttachment = new Attachment(vkCtx, width, height, DEPTH_FORMAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
    }

    public void cleanup(VkCtx vkCtx) {
        colorAttachments.forEach(a -> a.cleanup(vkCtx));
        deptAttachment.cleanup(vkCtx);
    }

    public List<Attachment> getAllAttachments() {
        List<Attachment> result = new ArrayList<>(colorAttachments);
        result.add(deptAttachment);
        return result;
    }

    public List<Attachment> getColorAttachments() {
        return colorAttachments;
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
