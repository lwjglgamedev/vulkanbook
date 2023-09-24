package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.*
import org.lwjgl.vulkan.KHRSwapchain.*
import org.lwjgl.vulkan.VK11.*
import org.vulkanb.eng.Window
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck
import java.util.*
import kotlin.math.max
import kotlin.math.min


private val logger = KotlinLogging.logger {}

class SwapChain(
    val device: Device,
    surface: Surface,
    window: Window,
    requestedImages: Int,
    vsync: Boolean,
    presentationQueue: Queue.PresentQueue,
    concurrentQueues: Array<Queue> = emptyArray()
) {
    val surfaceFormat: SurfaceFormat
    val swapChainExtent: VkExtent2D
    val imageViews: Array<ImageView>
    val vkSwapChain: Long
    val syncSemaphoresList: Array<SyncSemaphores>
    var currentFrame: Int

    init {
        logger.debug { "Creating Vulkan SwapChain" }
        MemoryStack.stackPush().use { stack ->
            val physicalDevice = device.physicalDevice

            // Get surface capabilities
            val surfCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack)
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.physicalDevice.vkPhysicalDevice, surface.vkSurface, surfCapabilities)
                .vkCheck("Failed to get surface capabilities")
            var numImages = calcNumImages(surfCapabilities, requestedImages)
            surfaceFormat = calcSurfaceFormat(physicalDevice, surface)
            swapChainExtent = calcSwapChainExtent(window, surfCapabilities)
            val vkSwapChainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface.vkSurface)
                .minImageCount(numImages)
                .imageFormat(surfaceFormat.imageFormat)
                .imageColorSpace(surfaceFormat.colorSpace)
                .imageExtent(swapChainExtent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(surfCapabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .clipped(true)
            if (vsync) {
                vkSwapChainCreateInfo.presentMode(VK_PRESENT_MODE_FIFO_KHR)
            } else {
                vkSwapChainCreateInfo.presentMode(VK_PRESENT_MODE_IMMEDIATE_KHR)
            }
            val indices: MutableList<Int> = ArrayList()
            for (queue in concurrentQueues) {
                if (queue.queueFamilyIndex != presentationQueue.queueFamilyIndex) {
                    indices.add(queue.queueFamilyIndex)
                }
            }
            if (indices.size > 0) {
                val intBuffer = stack.mallocInt(indices.size + 1)
                indices.forEach { i -> intBuffer.put(i) }
                intBuffer.put(presentationQueue.queueFamilyIndex).flip()
                vkSwapChainCreateInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                    .queueFamilyIndexCount(intBuffer.capacity())
                    .pQueueFamilyIndices(intBuffer)
            } else {
                vkSwapChainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
            }
            val lp = stack.mallocLong(1)
            vkCreateSwapchainKHR(device.vkDevice, vkSwapChainCreateInfo, null, lp)
                .vkCheck("Failed to create swap chain")
            vkSwapChain = lp.get(0)
            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat)
            numImages = imageViews.size
            syncSemaphoresList = Array(numImages) { SyncSemaphores(device) }
            currentFrame = 0
        }
    }

    fun acquireNextImage(): Boolean {
        var resize = false
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            val err = vkAcquireNextImageKHR(
                device.vkDevice,
                vkSwapChain,
                Long.MAX_VALUE,
                syncSemaphoresList[currentFrame].imgAcquisitionSemaphore.vkSemaphore,
                MemoryUtil.NULL,
                ip
            )
            if (err == VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true
            } else if (err == VK_SUBOPTIMAL_KHR) {
                // Not optimal but swapChain can still be used
            } else if (err != VK_SUCCESS) {
                throw java.lang.RuntimeException("Failed to acquire image: $err")
            }
            currentFrame = ip[0]
        }
        return resize
    }


    private fun calcNumImages(surfCapabilities: VkSurfaceCapabilitiesKHR, requestedImages: Int): Int {
        val maxImages: Int = surfCapabilities.maxImageCount()
        val minImages: Int = surfCapabilities.minImageCount()
        var result = minImages
        if (maxImages != 0) {
            result = min(requestedImages, maxImages)
        }
        result = max(result, minImages)
        logger.debug { "Requested [$requestedImages] images, got [$result] images. Surface capabilities, maxImages: [$maxImages], minImages [$minImages]" }
        return result
    }

    private fun calcSurfaceFormat(physicalDevice: PhysicalDevice, surface: Surface): SurfaceFormat {
        var imageFormat: Int
        var colorSpace: Int
        MemoryStack.stackPush().use { stack ->
            val ip = stack.mallocInt(1)
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vkPhysicalDevice, surface.vkSurface, ip, null)
                .vkCheck("Failed to get the number surface formats")
            val numFormats: Int = ip.get(0)
            if (numFormats <= 0) {
                throw RuntimeException("No surface formats retrieved")
            }
            val surfaceFormats: VkSurfaceFormatKHR.Buffer = VkSurfaceFormatKHR.calloc(numFormats, stack)
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vkPhysicalDevice, surface.vkSurface, ip, surfaceFormats)
                .vkCheck("Failed to get surface formats")
            imageFormat = VK_FORMAT_B8G8R8A8_SRGB
            colorSpace = surfaceFormats.get(0).colorSpace()
            for (i in 0..<numFormats) {
                val surfaceFormatKHR: VkSurfaceFormatKHR = surfaceFormats.get(i)
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                    surfaceFormatKHR.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
                ) {
                    imageFormat = surfaceFormatKHR.format()
                    colorSpace = surfaceFormatKHR.colorSpace()
                    break
                }
            }
        }
        return SurfaceFormat(imageFormat, colorSpace)
    }

    private fun calcSwapChainExtent(window: Window, surfCapabilities: VkSurfaceCapabilitiesKHR): VkExtent2D {
        val result = VkExtent2D.calloc()
        if (surfCapabilities.currentExtent().width() == -0x1) { // TODO: In Java was 0xFFFFFFFF
            // Surface size undefined. Set to the window size if within bounds
            var width = min(window.width.toDouble(), surfCapabilities.maxImageExtent().width().toDouble())
                .toInt()
            width = max(width.toDouble(), surfCapabilities.minImageExtent().width().toDouble()).toInt()
            var height = min(window.height.toDouble(), surfCapabilities.maxImageExtent().height().toDouble())
                .toInt()
            height = max(height.toDouble(), surfCapabilities.minImageExtent().height().toDouble()).toInt()
            result.width(width)
            result.height(height)
        } else {
            // Surface already defined, just use that for the swap chain
            result.set(surfCapabilities.currentExtent())
        }
        return result
    }

    fun cleanup() {
        logger.debug { "Destroying Vulkan SwapChain" }
        swapChainExtent.free()
        imageViews.forEach { it.cleanup() }
        syncSemaphoresList.forEach { it.cleanup() }
        vkDestroySwapchainKHR(device.vkDevice, vkSwapChain, null)
    }

    private fun createImageViews(stack: MemoryStack, device: Device, swapChain: Long, format: Int): Array<ImageView> {
        val result: Array<ImageView>
        val ip = stack.mallocInt(1)
        vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, null)
            .vkCheck("Failed to get number of surface images")
        val numImages = ip.get(0)
        val swapChainImages = stack.mallocLong(numImages)
        vkGetSwapchainImagesKHR(device.vkDevice, swapChain, ip, swapChainImages)
            .vkCheck("Failed to get surface images")
        val imageViewData = ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        result = Array(numImages) { i -> ImageView(device, swapChainImages.get(i), imageViewData) }
        return result
    }

    val numImages: Int
        get() = imageViews.size

    fun presentImage(queue: Queue): Boolean {
        var resize = false
        MemoryStack.stackPush().use { stack ->
            val present = VkPresentInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(syncSemaphoresList[currentFrame].renderCompleteSemaphore.vkSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(vkSwapChain))
                .pImageIndices(stack.ints(currentFrame))
            val err = vkQueuePresentKHR(queue.vkQueue, present)
            if (err == VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true
            } else if (err == VK_SUBOPTIMAL_KHR) {
                // Not optimal but swap chain can still be used
            } else if (err != VK_SUCCESS) {
                throw java.lang.RuntimeException("Failed to present KHR: $err")
            }
        }
        currentFrame = (currentFrame + 1) % imageViews.size
        return resize
    }

    data class SurfaceFormat(val imageFormat: Int, val colorSpace: Int)

    data class SyncSemaphores(val imgAcquisitionSemaphore: Semaphore, val renderCompleteSemaphore: Semaphore) {
        constructor(device: Device) : this(Semaphore(device), Semaphore(device))

        fun cleanup() {
            imgAcquisitionSemaphore.cleanup()
            renderCompleteSemaphore.cleanup()
        }
    }
}
