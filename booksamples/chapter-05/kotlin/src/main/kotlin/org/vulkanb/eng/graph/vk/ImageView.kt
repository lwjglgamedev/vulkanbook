package org.vulkanb.eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkImageSubresourceRange
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

class ImageView(private val device: Device, vkImage: Long, imageViewData: ImageViewData) {
    val aspectMask: Int
    val mipLevels: Int
    val vkImageView: Long

    init {
        aspectMask = imageViewData.aspectMask
        mipLevels = imageViewData.mipLevels
        MemoryStack.stackPush().use { stack ->
            val lp = stack.mallocLong(1)
            val viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(vkImage)
                .viewType(imageViewData.viewType)
                .format(imageViewData.format)
                .subresourceRange { it: VkImageSubresourceRange ->
                    it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(imageViewData.baseArrayLayer)
                        .layerCount(imageViewData.layerCount)
                }
            vkCreateImageView(device.vkDevice, viewCreateInfo, null, lp)
                .vkCheck("Failed to create image view")
            vkImageView = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyImageView(device.vkDevice, vkImageView, null)
    }

    // TODO Can use kotlin Builder pattern
    // https://kotlinlang.org/docs/using-builders-with-builder-inference.html
    class ImageViewData {
        var aspectMask = 0
        var baseArrayLayer = 0
        var format = 0
        var layerCount = 1
        var mipLevels = 1
        var viewType: Int

        init {
            viewType = VK_IMAGE_VIEW_TYPE_2D
        }

        fun aspectMask(aspectMask: Int): ImageViewData {
            this.aspectMask = aspectMask
            return this
        }

        fun baseArrayLayer(baseArrayLayer: Int): ImageViewData {
            this.baseArrayLayer = baseArrayLayer
            return this
        }

        fun format(format: Int): ImageViewData {
            this.format = format
            return this
        }

        fun layerCount(layerCount: Int): ImageViewData {
            this.layerCount = layerCount
            return this
        }

        fun mipLevels(mipLevels: Int): ImageViewData {
            this.mipLevels = mipLevels
            return this
        }

        fun viewType(viewType: Int): ImageViewData {
            this.viewType = viewType
            return this
        }
    }
}
