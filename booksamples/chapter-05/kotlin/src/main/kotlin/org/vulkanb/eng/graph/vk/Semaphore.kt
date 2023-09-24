package org.vulkanb.eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkSemaphoreCreateInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

class Semaphore(private val device: Device) {
    val vkSemaphore: Long

    init {
        MemoryStack.stackPush().use { stack ->
            val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
            val lp = stack.mallocLong(1)
            vkCreateSemaphore(device.vkDevice, semaphoreCreateInfo, null, lp)
                .vkCheck("Failed to create semaphore")
            vkSemaphore = lp[0]
        }
    }

    fun cleanup() {
        vkDestroySemaphore(device.vkDevice, vkSemaphore, null)
    }
}
