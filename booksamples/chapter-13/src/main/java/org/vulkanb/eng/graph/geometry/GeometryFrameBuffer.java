package org.vulkanb.eng.graph.geometry;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.tinylog.Logger;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.List;

public class GeometryFrameBuffer {

    private final GeometryRenderPass geometryRenderPass;

    private FrameBuffer frameBuffer;
    private GeometryAttachments geometryAttachments;

    public GeometryFrameBuffer(SwapChain swapChain) {
        Logger.debug("Creating GeometryFrameBuffer");
        createAttachments(swapChain);
        geometryRenderPass = new GeometryRenderPass(swapChain.getDevice(), geometryAttachments.getAttachments());
        createFrameBuffer(swapChain);
    }

    public void cleanup() {
        Logger.debug("Destroying Geometry FrameBuffer");
        geometryRenderPass.cleanup();
        geometryAttachments.cleanup();
        frameBuffer.cleanup();
    }

    private void createAttachments(SwapChain swapChain) {
        VkExtent2D extent2D = swapChain.getSwapChainExtent();
        int width = extent2D.width();
        int height = extent2D.height();
        geometryAttachments = new GeometryAttachments(swapChain.getDevice(), width, height);
    }

    private void createFrameBuffer(SwapChain swapChain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<Attachment> attachments = geometryAttachments.getAttachments();
            LongBuffer attachmentsBuff = stack.mallocLong(attachments.size());
            for (Attachment attachment : attachments) {
                attachmentsBuff.put(attachment.getImageView().getVkImageView());
            }
            attachmentsBuff.flip();

            frameBuffer = new FrameBuffer(swapChain.getDevice(), geometryAttachments.getWidth(), geometryAttachments.getHeight(),
                    attachmentsBuff, geometryRenderPass.getVkRenderPass(), 1);
        }
    }

    public GeometryAttachments geometryAttachments() {
        return geometryAttachments;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public GeometryRenderPass getRenderPass() {
        return geometryRenderPass;
    }

    public void resize(SwapChain swapChain) {
        frameBuffer.cleanup();
        geometryAttachments.cleanup();
        createAttachments(swapChain);
        createFrameBuffer(swapChain);
    }
}
