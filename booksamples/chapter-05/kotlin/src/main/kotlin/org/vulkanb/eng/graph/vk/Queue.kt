package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkSubmitInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck
import java.nio.IntBuffer
import java.nio.LongBuffer


private val logger = KotlinLogging.logger {}

open class Queue(device: Device, val queueFamilyIndex: Int, queueIndex: Int) {
    val vkQueue: VkQueue

    init {
        logger.debug { "Creating queue" }
        MemoryStack.stackPush().use { stack ->
            val pQueue = stack.mallocPointer(1)
            vkGetDeviceQueue(device.vkDevice, queueFamilyIndex, queueIndex, pQueue)
            val queue = pQueue.get(0)
            vkQueue = VkQueue(queue, device.vkDevice)
        }
    }

    fun submit(
        commandBuffers: PointerBuffer?, waitSemaphores: LongBuffer?, dstStageMasks: IntBuffer?,
        signalSemaphores: LongBuffer?, fence: Fence?
    ) {
        MemoryStack.stackPush().use { stack ->
            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(commandBuffers)
                .pSignalSemaphores(signalSemaphores)
            if (waitSemaphores != null) {
                submitInfo
                    .waitSemaphoreCount(waitSemaphores.capacity())
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(dstStageMasks)
            } else {
                submitInfo.waitSemaphoreCount(0)
            }
            val fenceHandle = fence?.vkFence ?: VK_NULL_HANDLE
            vkQueueSubmit(vkQueue, submitInfo, fenceHandle)
                .vkCheck("Failed to submit command to queue")
        }
    }
    fun waitIdle() {
        vkQueueWaitIdle(vkQueue)
    }

    class GraphicsQueue(device: Device, queueIndex: Int) : Queue(device, getGraphicsQueueFamilyIndex(device), queueIndex) {
        companion object {
            private fun getGraphicsQueueFamilyIndex(device: Device): Int {
                var index = -1
                val physicalDevice = device.physicalDevice
                val queuePropsBuff = physicalDevice.vkQueueFamilyProps
                val numQueuesFamilies = queuePropsBuff.capacity()
                for (i in 0..<numQueuesFamilies) {
                    val props = queuePropsBuff.get(i)
                    val graphicsQueue = props.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0
                    if (graphicsQueue) {
                        index = i
                        break
                    }
                }
                if (index < 0) {
                    throw RuntimeException("Failed to get graphics Queue family index")
                }
                return index
            }
        }
    }

    class PresentQueue(device: Device, surface: Surface, queueIndex: Int) : Queue(device, getPresentQueueFamilyIndex(device, surface), queueIndex) {
        companion object {
            private fun getPresentQueueFamilyIndex(device: Device, surface: Surface): Int {
                var index = -1
                MemoryStack.stackPush().use { stack ->
                    val physicalDevice = device.physicalDevice
                    val queuePropsBuff = physicalDevice.vkQueueFamilyProps
                    val numQueuesFamilies = queuePropsBuff.capacity()
                    val intBuff = stack.mallocInt(1)
                    for (i in 0..<numQueuesFamilies) {
                        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice.vkPhysicalDevice, i, surface.vkSurface, intBuff)
                        val supportsPresentation = intBuff[0] == VK_TRUE
                        if (supportsPresentation) {
                            index = i
                            break
                        }
                    }
                }
                if (index < 0) {
                    throw java.lang.RuntimeException("Failed to get Presentation Queue family index")
                }
                return index
            }
        }
    }
}
