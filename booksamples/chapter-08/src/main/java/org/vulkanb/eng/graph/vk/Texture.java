package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.*;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Texture {

    // RGBA
    private static final int BYTES_PER_PIXEL = 4;
    private String fileName;
    private int height;
    private Image image;
    private ImageView imageView;
    private int mipLevels;
    private int width;

    public Texture(CommandPool commandPool, Queue queue, String fileName, int imageFormat) {
        Device device = commandPool.getDevice();
        this.fileName = fileName;
        ByteBuffer buf;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            buf = stbi_load(fileName, w, h, channels, 4);
            if (buf == null) {
                throw new RuntimeException("Image file [" + fileName + "] not loaded: " + stbi_failure_reason());
            }

            width = w.get();
            height = h.get();
            mipLevels = 1;

            VulkanBuffer bufferData = createImage(stack, device, buf, imageFormat);
            transitionImageLayout(stack, commandPool, queue, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
            copyBufferToImage(stack, commandPool, queue, bufferData);
            transitionImageLayout(stack, commandPool, queue, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            imageView = new ImageView(device, image.getVkImage(), image.getFormat(), VK_IMAGE_ASPECT_COLOR_BIT, mipLevels);

            bufferData.cleanup();
        }

        stbi_image_free(buf);
    }

    public void cleanup() {
        imageView.cleanup();
        image.cleanup();
    }

    void copyBufferToImage(MemoryStack stack, CommandPool commandPool, Queue queue, VulkanBuffer bufferData) {

        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();

        VkBufferImageCopy.Buffer region = VkBufferImageCopy.callocStack(1, stack)
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

        cmd.endRecording();
        queue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, null);
        queue.waitIdle();
        cmd.cleanup();
    }

    private VulkanBuffer createImage(MemoryStack stack, Device device, ByteBuffer data, int imageFormat) {
        int size = width * height * BYTES_PER_PIXEL;
        VulkanBuffer bufferData = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        PointerBuffer pp = stack.mallocPointer(1);
        vkCheck(vkMapMemory(device.getVkDevice(), bufferData.getMemory(), 0,
                bufferData.getAllocationSize(), 0, pp), "Failed to map memory");

        ByteBuffer buffer = pp.getByteBuffer(size);
        buffer.put(data);
        data.flip();

        vkUnmapMemory(device.getVkDevice(), bufferData.getMemory());

        image = new Image(device, width, height, imageFormat,
                VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT,
                mipLevels, 1);

        return bufferData;
    }

    public String getFileName() {
        return fileName;
    }

    public ImageView getImageView() {
        return imageView;
    }

    private void transitionImageLayout(MemoryStack stack, CommandPool commandPool, Queue queue,
                                       int oldLayout, int newLayout) {

        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.callocStack(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image.getVkImage())
                .subresourceRange(it -> it
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1));

        int sourceStage;
        int destinationStage;

        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            barrier.srcAccessMask(0);
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);

            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
            barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

            sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
            destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        } else {
            throw new RuntimeException("Unsupported layout transition");
        }

        vkCmdPipelineBarrier(
                cmd.getVkCommandBuffer(),
                sourceStage, destinationStage,
                0,
                null,
                null,
                barrier);

        cmd.endRecording();
        queue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, null);
        queue.waitIdle();
        cmd.cleanup();
    }
}
