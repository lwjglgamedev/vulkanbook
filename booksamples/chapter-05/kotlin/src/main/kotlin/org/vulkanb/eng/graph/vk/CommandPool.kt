package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkCommandPoolCreateInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

private val logger = KotlinLogging.logger {}

class CommandPool(val device: Device, queueFamilyIndex: Int) {
    val vkCommandPool: Long

    init {
        logger.debug { "Creating Vulkan CommandPool" }
        MemoryStack.stackPush().use { stack ->
            val cmdPoolInfo: VkCommandPoolCreateInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamilyIndex)
            val lp = stack.mallocLong(1)
            vkCreateCommandPool(device.vkDevice, cmdPoolInfo, null, lp)
                .vkCheck("Failed to create command pool")
            vkCommandPool = lp.get(0)
        }
    }

    fun cleanup() {
        vkDestroyCommandPool(device.vkDevice, vkCommandPool, null)
    }
}
