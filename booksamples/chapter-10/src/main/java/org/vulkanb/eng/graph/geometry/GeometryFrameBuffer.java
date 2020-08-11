package org.vulkanb.eng.graph.geometry;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

public class GeometryFrameBuffer {

    private static final Logger LOGGER = LogManager.getLogger();

    private FrameBuffer frameBuffer;
    private GeometryAttachments geometryAttachments;
    private GeometryRenderPass geometryRenderPass;

    public GeometryFrameBuffer(SwapChain swapChain) {
        LOGGER.debug("Creating GeometryFrameBuffer");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = swapChain.getDevice();
            VkExtent2D extent2D = swapChain.getSwapChainExtent();
            int width = extent2D.width();
            int height = extent2D.height();

            geometryAttachments = new GeometryAttachments(device, width, height);
            geometryRenderPass = new GeometryRenderPass(device, geometryAttachments);

            Attachment[] attachments = geometryAttachments.getAttachments();
            int numAttachments = attachments.length;
            LongBuffer attachmentsBuff = stack.mallocLong(numAttachments);
            for (int i = 0; i < numAttachments; i++) {
                attachmentsBuff.put(i, attachments[i].getImageView().getVkImageView());
            }

            frameBuffer = new FrameBuffer(device, width, height, attachmentsBuff,
                    geometryRenderPass.getVkRenderPass());
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Geometry FrameBuffer");
        geometryRenderPass.cleanup();
        geometryAttachments.cleanup();
        frameBuffer.cleanup();
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
}
