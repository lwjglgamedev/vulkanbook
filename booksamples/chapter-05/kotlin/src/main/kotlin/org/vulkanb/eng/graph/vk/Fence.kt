package org.vulkanb.eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

class Fence(private val device: Device, signaled: Boolean) {
    val vkFence: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(if (signaled) VK_FENCE_CREATE_SIGNALED_BIT else 0)
            val lp = stack.mallocLong(1)
            vkCreateFence(device.vkDevice, fenceCreateInfo, null, lp)
                .vkCheck("Failed to create semaphore")
            vkFence = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyFence(device.vkDevice, vkFence, null)
    }

    fun fenceWait() {
        vkWaitForFences(device.vkDevice, vkFence, true, Long.MAX_VALUE)
    }

    fun reset() {
        vkResetFences(device.vkDevice, vkFence)
    }
}
