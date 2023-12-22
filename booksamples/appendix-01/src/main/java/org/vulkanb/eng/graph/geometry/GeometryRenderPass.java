package org.vulkanb.eng.graph.geometry;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class GeometryRenderPass {

    private static final int MAX_SAMPLES = 1;

    private final Device device;
    private final long vkRenderPass;

    public GeometryRenderPass(Device device, List<Attachment> attachments) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int numAttachments = attachments.size();
            VkAttachmentDescription.Buffer attachmentsDesc = VkAttachmentDescription.calloc(numAttachments, stack);
            int depthAttachmentPos = 0;
            for (int i = 0; i < numAttachments; i++) {
                Attachment attachment = attachments.get(i);
                attachmentsDesc.get(i)
                        .format(attachment.getImage().getFormat())
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .samples(MAX_SAMPLES)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                if (attachment.isDepthAttachment()) {
                    depthAttachmentPos = i;
                    attachmentsDesc.get(i).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL);
                } else {
                    attachmentsDesc.get(i).finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                }
            }

            VkAttachmentReference.Buffer colorReferences = VkAttachmentReference.calloc(GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                    stack);
            for (int i = 0; i < GeometryAttachments.NUMBER_COLOR_ATTACHMENTS; i++) {
                colorReferences.get(i)
                        .attachment(i)
                        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }

            VkAttachmentReference depthReference = VkAttachmentReference.calloc(stack)
                    .attachment(depthAttachmentPos)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            // Render subpass
            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .pColorAttachments(colorReferences)
                    .colorAttachmentCount(colorReferences.capacity())
                    .pDepthStencilAttachment(depthReference);

            // Subpass dependencies
            VkSubpassDependency.Buffer subpassDependencies = VkSubpassDependency.calloc(2, stack);
            subpassDependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            subpassDependencies.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            // Render pass
            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachmentsDesc)
                    .pSubpasses(subpass)
                    .pDependencies(subpassDependencies);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateRenderPass(device.getVkDevice(), renderPassInfo, null, lp),
                    "Failed to create render pass");
            vkRenderPass = lp.get(0);
        }
    }

    public void cleanup() {
        vkDestroyRenderPass(device.getVkDevice(), vkRenderPass, null);
    }

    public long getVkRenderPass() {
        return vkRenderPass;
    }
}