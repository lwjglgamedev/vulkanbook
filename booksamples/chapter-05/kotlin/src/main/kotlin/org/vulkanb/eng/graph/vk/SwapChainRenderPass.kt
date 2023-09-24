package org.vulkanb.eng.graph.vk

import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

class SwapChainRenderPass(private val swapChain: SwapChain) {
    var vkRenderPass: Long = 0

    init {
        MemoryStack.stackPush().use { stack ->
            val attachments = VkAttachmentDescription.calloc(1, stack)

            // Color attachment
            attachments[0]
                .format(swapChain.surfaceFormat.imageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            val colorReference = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            val subPass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(colorReference.remaining())
                .pColorAttachments(colorReference)

            val subpassDependencies = VkSubpassDependency.calloc(1, stack)
            subpassDependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subPass)
                .pDependencies(subpassDependencies)

            val lp = stack.mallocLong(1)
            vkCreateRenderPass(swapChain.device.vkDevice, renderPassInfo, null, lp)
                .vkCheck("Failed to create render pass")
            vkRenderPass = lp[0]
        }
    }

    fun cleanup() {
        vkDestroyRenderPass(swapChain.device.vkDevice, vkRenderPass, null)
    }
}