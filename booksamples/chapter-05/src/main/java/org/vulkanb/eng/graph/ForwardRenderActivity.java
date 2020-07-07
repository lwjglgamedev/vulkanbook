package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;

public class ForwardRenderActivity {

    private CommandBuffer[] commandBuffers;
    private Fence[] fences;
    private FrameBuffer[] frameBuffers;
    private SwapChainRenderPass renderPass;
    private SwapChain swapChain;

    public ForwardRenderActivity(SwapChain swapChain, CommandPool commandPool) {
        this.swapChain = swapChain;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = swapChain.getDevice();
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            ImageView[] imageViews = swapChain.getImageViews();
            int numImages = imageViews.length;

            this.renderPass = new SwapChainRenderPass(swapChain);

            LongBuffer pAttachments = stack.mallocLong(1);
            this.frameBuffers = new FrameBuffer[numImages];
            for (int i = 0; i < numImages; i++) {
                pAttachments.put(0, imageViews[i].getVkImageView());
                this.frameBuffers[i] = new FrameBuffer(device, swapChainExtent.width(), swapChainExtent.height(),
                        pAttachments, this.renderPass.getVkRenderPass());
            }

            this.commandBuffers = new CommandBuffer[numImages];
            this.fences = new Fence[numImages];
            for (int i = 0; i < numImages; i++) {
                this.commandBuffers[i] = new CommandBuffer(commandPool, true, false);
                this.fences[i] = new Fence(device, true);
                recordCommandBuffer(this.commandBuffers[i], this.frameBuffers[i], swapChainExtent.width(), swapChainExtent.height());
            }
        }
    }

    public void cleanUp() {
        Arrays.stream(this.frameBuffers).forEach(FrameBuffer::cleanUp);
        this.renderPass.cleanUp();
        Arrays.stream(this.commandBuffers).forEach(CommandBuffer::cleanUp);
        Arrays.stream(this.fences).forEach(Fence::cleanUp);
    }

    private void recordCommandBuffer(CommandBuffer commandBuffer, FrameBuffer frameBuffer, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
            clearValues.apply(0, v -> v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1));
            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(this.renderPass.getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            commandBuffer.beginRecording();
            vkCmdBeginRenderPass(commandBuffer.getVkCommandBuffer(), renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdEndRenderPass(commandBuffer.getVkCommandBuffer());
            commandBuffer.endRecording();
        }
    }

    public void submit(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = this.swapChain.getCurrentFrame();
            CommandBuffer commandBuffer = this.commandBuffers[idx];
            Fence currentFence = this.fences[idx];
            currentFence.fenceWait();
            currentFence.reset();
            SwapChain.SyncSemaphores syncSemaphores = this.swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphores().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphores().getVkSemaphore()), currentFence);

        }
    }
}