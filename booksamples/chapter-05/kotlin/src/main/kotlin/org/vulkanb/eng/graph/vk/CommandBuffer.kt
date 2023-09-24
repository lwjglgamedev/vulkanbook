package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

private val logger = KotlinLogging.logger {}

class CommandBuffer(private val commandPool: CommandPool, private val primary: Boolean, private val oneTimeSubmit: Boolean) {
    val vkCommandBuffer: VkCommandBuffer

    init {
        logger.trace { "Creating command buffer" }
        val vkDevice = commandPool.device.vkDevice
        MemoryStack.stackPush().use { stack ->
            val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool.vkCommandPool)
                .level(if (primary) VK_COMMAND_BUFFER_LEVEL_PRIMARY else VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                .commandBufferCount(1)
            val pb = stack.mallocPointer(1)
            vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb)
                .vkCheck("Failed to allocate render command buffer")
            vkCommandBuffer = VkCommandBuffer(pb.get(0), vkDevice)
        }
    }

    fun beginRecording(inheritanceInfo: InheritanceInfo? = null) {
        MemoryStack.stackPush().use { stack ->
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            if (oneTimeSubmit) {
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
            }
            if (!primary) {
                if (inheritanceInfo == null) {
                    throw RuntimeException("Secondary buffers must declare inheritance info")
                }
                val vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO)
                    .renderPass(inheritanceInfo.vkRenderPass)
                    .subpass(inheritanceInfo.subPass)
                    .framebuffer(inheritanceInfo.vkFrameBuffer)
                cmdBufInfo.pInheritanceInfo(vkInheritanceInfo)
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_RENDER_PASS_CONTINUE_BIT)
            }
            vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo)
                .vkCheck("Failed to begin command buffer")
        }
    }

    fun cleanup() {
        logger.trace { "Destroying command buffer" }
        vkFreeCommandBuffers(commandPool.device.vkDevice, commandPool.vkCommandPool, vkCommandBuffer)
    }

    fun endRecording() {
        vkEndCommandBuffer(vkCommandBuffer)
            .vkCheck("Failed to end command buffer")
    }

    fun reset() {
        vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT)
    }

    data class InheritanceInfo(val vkRenderPass: Long, val vkFrameBuffer: Long, val subPass: Int)
}
