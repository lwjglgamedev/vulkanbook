package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*

private val logger = KotlinLogging.logger {}

class Surface(private val physicalDevice: PhysicalDevice, windowHandle: Long) {
    var vkSurface: Long = 0

    init {
        logger.debug { "Creating Vulkan surface" }
        MemoryStack.stackPush().use { stack ->
            val pSurface = stack.mallocLong(1)
            GLFWVulkan.glfwCreateWindowSurface(physicalDevice.vkPhysicalDevice.instance, windowHandle, null, pSurface)
            vkSurface = pSurface.get(0)
        }
    }

    fun cleanup() {
        logger.debug { "Destroying Vulkan surface" }
        KHRSurface.vkDestroySurfaceKHR(physicalDevice.vkPhysicalDevice.instance, vkSurface, null)
    }
}
