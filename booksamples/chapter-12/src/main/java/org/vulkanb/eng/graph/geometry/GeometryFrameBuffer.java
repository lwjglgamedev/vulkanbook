package org.vulkanb.eng.graph.geometry;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;

public class GeometryFrameBuffer {

    private static final Logger LOGGER = LogManager.getLogger();

    private FrameBuffer frameBuffer;
    private GeometryAttachments geometryAttachments;
    private GeometryRenderPass geometryRenderPass;
    private int numSamples;

    public GeometryFrameBuffer(SwapChain swapChain) {
        LOGGER.debug("Creating GeometryFrameBuffer");
        createAttachments(swapChain);
        geometryRenderPass = new GeometryRenderPass(swapChain.getDevice(), geometryAttachments.getAttachments());
        createFrameBuffer(swapChain);
    }

    public void cleanup() {
        LOGGER.debug("Destroying Geometry FrameBuffer");
        geometryRenderPass.cleanup();
        geometryAttachments.cleanup();
        frameBuffer.cleanup();
    }

    private void createAttachments(SwapChain swapChain) {
        VkExtent2D extent2D = swapChain.getSwapChainExtent();
        int width = extent2D.width();
        int height = extent2D.height();

        numSamples = VK_SAMPLE_COUNT_1_BIT;
        EngineProperties engProps = EngineProperties.getInstance();
        int requestedSamples = engProps.getMultiSampling();
        Device device = swapChain.getDevice();
        if (requestedSamples > 0 && device.isSampleRateShading()) {
            numSamples = device.getPhysicalDevice().supportsSampleCount(requestedSamples) ? requestedSamples : VK_SAMPLE_COUNT_1_BIT;
        }
        LOGGER.debug("Requested [{}] samples, using [{}]", requestedSamples, numSamples);

        geometryAttachments = new GeometryAttachments(swapChain.getDevice(), width, height, numSamples);
    }

    private void createFrameBuffer(SwapChain swapChain) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Attachment[] attachments = geometryAttachments.getAttachments();
            int numAttachments = attachments.length;
            LongBuffer attachmentsBuff = stack.mallocLong(numAttachments);
            for (int i = 0; i < numAttachments; i++) {
                attachmentsBuff.put(i, attachments[i].getImageView().getVkImageView());
            }

            frameBuffer = new FrameBuffer(swapChain.getDevice(), geometryAttachments.getWidth(), geometryAttachments.getHeight(),
                    attachmentsBuff, geometryRenderPass.getVkRenderPass());
        }
    }

    public GeometryAttachments geometryAttachments() {
        return geometryAttachments;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public int getNumSamples() {
        return numSamples;
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

    public void setNumSamples(int numSamples) {
        this.numSamples = numSamples;
    }
}
