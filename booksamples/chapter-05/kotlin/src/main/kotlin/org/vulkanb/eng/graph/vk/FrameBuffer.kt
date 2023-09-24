package org.vulkanb.eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkFramebufferCreateInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck
import java.nio.LongBuffer

class FrameBuffer(private val device: Device, width: Int, height: Int, pAttachments: LongBuffer?, renderPass: Long) {
    var vkFrameBuffer: Long = 0

    init {
        MemoryStack.stackPush().use { stack ->
            val fci = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .pAttachments(pAttachments)
                .width(width)
                .height(height)
                .layers(1)
                .renderPass(renderPass)
            val lp = stack.mallocLong(1)
            vkCreateFramebuffer(device.vkDevice, fci, null, lp)
                .vkCheck("Failed to create FrameBuffer")
            vkFrameBuffer = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFramebuffer(device.vkDevice, vkFrameBuffer, null)
    }
}