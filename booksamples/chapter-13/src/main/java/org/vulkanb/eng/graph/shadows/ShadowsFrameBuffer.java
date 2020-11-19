package org.vulkanb.eng.graph.shadows;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.VK_IMAGE_VIEW_TYPE_2D_ARRAY;

public class ShadowsFrameBuffer {

    private static final Logger LOGGER = LogManager.getLogger();

    private Attachment depthAttachment;
    private FrameBuffer frameBuffer;
    private ShadowsRenderPass shadowsRenderPass;

    public ShadowsFrameBuffer(Device device, Image depthImage, ImageView depthImageView, int baseArrayLayer) {
        LOGGER.debug("Creating ShadowsFrameBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ImageView imageView = new ImageView(device, depthImage.getVkImage(), depthImage.getFormat(),
                    depthImageView.getAspectMask(), depthImageView.getMipLevels(), VK_IMAGE_VIEW_TYPE_2D_ARRAY,
                    baseArrayLayer, 1);
            depthAttachment = new Attachment(depthImage, imageView, true);

            shadowsRenderPass = new ShadowsRenderPass(device, depthAttachment);

            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            LongBuffer attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, depthAttachment.getImageView().getVkImageView());
            frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff,
                    shadowsRenderPass.getVkRenderPass());
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying ShadowsFrameBuffer");
        shadowsRenderPass.cleanup();
        depthAttachment.getImageView().cleanup();
        frameBuffer.cleanup();
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public ShadowsRenderPass getRenderPass() {
        return shadowsRenderPass;
    }
}
