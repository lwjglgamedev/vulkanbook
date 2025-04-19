package org.vulkanb.eng.graph.scn;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.util.Arrays;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR;
import static org.lwjgl.vulkan.VK13.*;

public class ScnRender {

    private final VkClearValue clrValueColor;
    private VkRenderingAttachmentInfo.Buffer[] attInfoColor;
    private VkRenderingInfo[] renderInfo;

    public ScnRender(VkCtx vkCtx) {
        clrValueColor = VkClearValue.calloc().color(
                c -> c.float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1.0f));
        attInfoColor = createColorAttachmentsInfo(vkCtx, clrValueColor);
        renderInfo = createRenderInfo(vkCtx, attInfoColor);
    }

    private static VkRenderingAttachmentInfo.Buffer[] createColorAttachmentsInfo(VkCtx vkCtx, VkClearValue clearValue) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingAttachmentInfo.Buffer[numImages];

        for (int i = 0; i < numImages; ++i) {
            var attachments = VkRenderingAttachmentInfo.calloc(1)
                    .sType$Default()
                    .imageView(swapChain.getImageView(i).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);
            result[i] = attachments;
        }
        return result;
    }

    private static VkRenderingInfo[] createRenderInfo(VkCtx vkCtx, VkRenderingAttachmentInfo.Buffer[] attachments) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingInfo[numImages];

        try (var stack = MemoryStack.stackPush()) {
            VkExtent2D extent = swapChain.getSwapChainExtent();
            var renderArea = VkRect2D.calloc(stack).extent(extent);

            for (int i = 0; i < numImages; ++i) {
                var renderingInfo = VkRenderingInfo.calloc()
                        .sType$Default()
                        .renderArea(renderArea)
                        .layerCount(1)
                        .pColorAttachments(attachments[i]);
                result[i] = renderingInfo;
            }
        }
        return result;
    }

    public void cleanup() {
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(attInfoColor).forEach(VkRenderingAttachmentInfo.Buffer::free);
        clrValueColor.free();
    }

    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();

            long swapChainImage = swapChain.getImageView(imageIndex).getVkImage();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            vkCmdBeginRendering(cmdHandle, renderInfo[imageIndex]);

            vkCmdEndRendering(cmdHandle);

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }
}
