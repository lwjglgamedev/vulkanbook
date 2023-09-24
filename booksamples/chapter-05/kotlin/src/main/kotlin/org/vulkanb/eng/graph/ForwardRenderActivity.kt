package org.vulkanb.eng.graph

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.vulkanb.eng.graph.vk.*


class ForwardRenderActivity(private val swapChain: SwapChain, commandPool: CommandPool) {
    private val commandBuffers: Array<CommandBuffer>
    private val fences: Array<Fence>
    private val frameBuffers: Array<FrameBuffer>
    private val renderPass: SwapChainRenderPass

    init {
        MemoryStack.stackPush().use { stack ->
            val device = swapChain.device
            val swapChainExtent = swapChain.swapChainExtent
            val imageViews = swapChain.imageViews
            val numImages = imageViews.size

            renderPass = SwapChainRenderPass(swapChain)

            val pAttachments = stack.mallocLong(1)
            frameBuffers = Array(numImages) {
                pAttachments.put(0, imageViews[it].vkImageView)
                FrameBuffer(device, swapChainExtent.width(), swapChainExtent.height(), pAttachments, renderPass.vkRenderPass)
            }

            commandBuffers = Array(numImages) {
                val buffer = CommandBuffer(commandPool, primary = true, oneTimeSubmit = false)
                recordCommandBuffer(
                    buffer,
                    frameBuffers[it],
                    swapChainExtent.width(),
                    swapChainExtent.height()
                )
                buffer
            }

            fences = Array(numImages) { Fence(device, true) }
        }
    }

    fun cleanup() {
        frameBuffers.forEach { it.cleanup() }
        renderPass.cleanup()
        commandBuffers.forEach { it.cleanup() }
        fences.forEach { it.cleanup() }
    }

    private fun recordCommandBuffer(commandBuffer: CommandBuffer, frameBuffer: FrameBuffer, width: Int, height: Int) {
        MemoryStack.stackPush().use { stack ->
            val clearValues = VkClearValue.calloc(1, stack)
            clearValues.apply(0) { v: VkClearValue ->
                v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1f)
            }
            val renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass.vkRenderPass)
                .pClearValues(clearValues)
                .renderArea { a -> a.extent().set(width, height) }
                .framebuffer(frameBuffer.vkFrameBuffer)
            commandBuffer.beginRecording()
            vkCmdBeginRenderPass(
                commandBuffer.vkCommandBuffer,
                renderPassBeginInfo,
                VK_SUBPASS_CONTENTS_INLINE
            )
            vkCmdEndRenderPass(commandBuffer.vkCommandBuffer)
            commandBuffer.endRecording()
        }
    }

    fun submit(queue: Queue) {
        MemoryStack.stackPush().use { stack ->
            val idx = swapChain.currentFrame
            val commandBuffer = commandBuffers[idx]
            val currentFence = fences[idx]
            currentFence.fenceWait()
            currentFence.reset()
            val syncSemaphores = swapChain.syncSemaphoresList[idx]
            queue.submit(
                stack.pointers(commandBuffer.vkCommandBuffer),
                stack.longs(syncSemaphores.imgAcquisitionSemaphore.vkSemaphore),
                stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                stack.longs(syncSemaphores.renderCompleteSemaphore.vkSemaphore), currentFence
            )
        }
    }
}