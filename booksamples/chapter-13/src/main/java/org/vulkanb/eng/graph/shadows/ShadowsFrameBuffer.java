package org.vulkanb.eng.graph.shadows;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowsFrameBuffer {

    private static final Logger LOGGER = LogManager.getLogger();

    private Attachment depthAttachment;
    private FrameBuffer frameBuffer;
    private ShadowsRenderPass shadowsRenderPass;

    public ShadowsFrameBuffer(Device device) {
        LOGGER.debug("Creating ShadowsFrameBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int mipLevels = 1;
            int sampleCount = 1;
            int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            Image depthImage = new Image(device, shadowMapSize, shadowMapSize,
                    VK_FORMAT_D32_SFLOAT, usage | VK_IMAGE_USAGE_SAMPLED_BIT, mipLevels, sampleCount,
                    GraphConstants.SHADOW_MAP_CASCADE_COUNT);

            int aspectMask = Attachment.calcAspectMask(usage);

            ImageView depthImageView = new ImageView(device, depthImage.getVkImage(), depthImage.getFormat(),
                    aspectMask, 1, VK_IMAGE_VIEW_TYPE_2D_ARRAY, 0, GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            depthAttachment = new Attachment(depthImage, depthImageView, true);

            shadowsRenderPass = new ShadowsRenderPass(device, depthAttachment);

            LongBuffer attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, depthAttachment.getImageView().getVkImageView());
            frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff,
                    shadowsRenderPass.getVkRenderPass(), GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying ShadowsFrameBuffer");
        shadowsRenderPass.cleanup();
        depthAttachment.cleanup();
        frameBuffer.cleanup();
    }

    public Attachment getDepthAttachment() {
        return depthAttachment;
    }

    public FrameBuffer getFrameBuffer() {
        return frameBuffer;
    }

    public ShadowsRenderPass getRenderPass() {
        return shadowsRenderPass;
    }
}
