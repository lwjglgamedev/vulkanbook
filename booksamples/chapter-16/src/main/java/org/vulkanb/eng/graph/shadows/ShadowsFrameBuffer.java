package org.vulkanb.eng.graph.shadows;

import org.lwjgl.system.MemoryStack;
import org.tinylog.Logger;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowsFrameBuffer {

    private final Attachment depthAttachment;
    private final FrameBuffer frameBuffer;
    private final ShadowsRenderPass shadowsRenderPass;

    public ShadowsFrameBuffer(Device device) {
        Logger.debug("Creating ShadowsFrameBuffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            Image.ImageData imageData = new Image.ImageData().width(shadowMapSize).height(shadowMapSize).
                    usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                    format(VK_FORMAT_D32_SFLOAT).arrayLayers(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            Image depthImage = new Image(device, imageData);

            ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(depthImage.getFormat()).
                    aspectMask(Attachment.calcAspectMask(usage)).viewType(VK_IMAGE_VIEW_TYPE_2D_ARRAY).
                    baseArrayLayer(0).layerCount(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            ImageView depthImageView = new ImageView(device, depthImage.getVkImage(), imageViewData);
            depthAttachment = new Attachment(depthImage, depthImageView, true);

            shadowsRenderPass = new ShadowsRenderPass(device, depthAttachment);

            LongBuffer attachmentsBuff = stack.mallocLong(1);
            attachmentsBuff.put(0, depthAttachment.getImageView().getVkImageView());
            frameBuffer = new FrameBuffer(device, shadowMapSize, shadowMapSize, attachmentsBuff,
                    shadowsRenderPass.getVkRenderPass(), GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        }
    }

    public void cleanup() {
        Logger.debug("Destroying ShadowsFrameBuffer");
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
