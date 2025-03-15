package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK13.*;

public class Texture {

    private final int height;
    private final String id;
    private final Image image;
    private final ImageView imageView;
    private final int width;
    private boolean recordedTransition;
    private VkBuffer stgBuffer;

    public Texture(VkCtx vkCtx, String id, ImageSrc srcImage, int imageFormat) {
        this.id = id;
        recordedTransition = false;
        width = srcImage.width();
        height = srcImage.height();

        createStgBuffer(vkCtx, srcImage.data());
        var imageData = new Image.ImageData().width(width).height(height).
                usage(VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(imageFormat);
        image = new Image(vkCtx, imageData);
        var imageViewData = new ImageView.ImageViewData().format(image.getFormat()).
                aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        imageView = new ImageView(vkCtx.getDevice(), image.getVkImage(), imageViewData, false);
    }

    public void cleanup(VkCtx vkCtx) {
        cleanupStgBuffer(vkCtx);
        imageView.cleanup(vkCtx.getDevice());
        image.cleanup(vkCtx);
    }

    public void cleanupStgBuffer(VkCtx vkCtx) {
        if (stgBuffer != null) {
            stgBuffer.cleanup(vkCtx);
            stgBuffer = null;
        }
    }

    private void createStgBuffer(VkCtx vkCtx, ByteBuffer data) {
        int size = data.remaining();
        stgBuffer = new VkBuffer(vkCtx, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        long mappedMemory = stgBuffer.map(vkCtx);
        ByteBuffer buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgBuffer.getRequestedSize());
        buffer.put(data);
        data.flip();

        stgBuffer.unMap(vkCtx);
    }

    public int getHeight() {
        return height;
    }

    public String getId() {
        return id;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public int getWidth() {
        return width;
    }

    private void recordCopyBuffer(MemoryStack stack, CmdBuffer cmd, VkBuffer bufferData) {

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(it ->
                        it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                )
                .imageOffset(it -> it.x(0).y(0).z(0))
                .imageExtent(it -> it.width(width).height(height).depth(1));

        vkCmdCopyBufferToImage(cmd.getVkCommandBuffer(), bufferData.getBuffer(), image.getVkImage(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    }

    private void recordImageTransition(MemoryStack stack, CmdBuffer cmd) {
        var imageBarrier = VkImageMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcStageMask(VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .subresourceRange(it -> it
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(image.getMipLevels())
                        .baseArrayLayer(0)
                        .layerCount(1))
                .image(image.getVkImage());

        VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(imageBarrier);

        vkCmdPipelineBarrier2(cmd.getVkCommandBuffer(), depInfo);
    }

    private void recordLayoutTransition(MemoryStack stack, CmdBuffer cmd) {
        VkImageSubresourceRange subResourceRange = VkImageSubresourceRange.calloc(stack)
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseArrayLayer(0)
                .levelCount(1)
                .layerCount(1);

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .image(image.getVkImage())
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .subresourceRange(subResourceRange);

        barrier.subresourceRange(it -> it
                        .baseMipLevel(0))
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

        vkCmdPipelineBarrier(cmd.getVkCommandBuffer(),
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                null, null, barrier);
    }

    public void recordTextureTransition(CmdBuffer cmd) {
        if (stgBuffer != null && !recordedTransition) {
            recordedTransition = true;
            try (var stack = MemoryStack.stackPush()) {
                recordImageTransition(stack, cmd);
                recordCopyBuffer(stack, cmd, stgBuffer);
                recordLayoutTransition(stack, cmd);
            }
        }
    }
}
