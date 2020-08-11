package org.vulkanb.eng.graph.lighting;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkExtent2D;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.Arrays;

public class LightingFrameBuffer {
    private static final Logger LOGGER = LogManager.getLogger();
    private FrameBuffer[] frameBuffers;
    private LightingRenderPass lightingRenderPass;

    public LightingFrameBuffer(SwapChain swapChain) {
        LOGGER.debug("Creating Lighting FrameBuffer");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            lightingRenderPass = new LightingRenderPass(swapChain);

            VkExtent2D extent2D = swapChain.getSwapChainExtent();
            int width = extent2D.width();
            int height = extent2D.height();

            int numImages = swapChain.getNumImages();
            frameBuffers = new FrameBuffer[numImages];
            LongBuffer attachmentsBuff = stack.mallocLong(1);
            for (int i = 0; i < numImages; i++) {
                attachmentsBuff.put(0, swapChain.getImageViews()[i].getVkImageView());
                frameBuffers[i] = new FrameBuffer(swapChain.getDevice(), width, height,
                        attachmentsBuff, lightingRenderPass.getVkRenderPass());
            }
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Lighting FrameBuffer");
        Arrays.stream(frameBuffers).forEach(FrameBuffer::cleanup);
        lightingRenderPass.cleanup();
    }

    public FrameBuffer[] getFrameBuffers() {
        return frameBuffers;
    }

    public LightingRenderPass getLightingRenderPass() {
        return lightingRenderPass;
    }

}
