package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class CmdBuffer {

    private final boolean oneTimeSubmit;
    private final boolean primary;
    private final VkCommandBuffer vkCommandBuffer;

    public CmdBuffer(VkCtx vkCtx, CmdPool cmdPool, boolean primary, boolean oneTimeSubmit) {
        Logger.trace("Creating command buffer");
        this.primary = primary;
        this.oneTimeSubmit = oneTimeSubmit;
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();

        try (var stack = MemoryStack.stackPush()) {
            var cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(cmdPool.getVkCommandPool())
                    .level(primary ? VK_COMMAND_BUFFER_LEVEL_PRIMARY : VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                    .commandBufferCount(1);
            PointerBuffer pb = stack.mallocPointer(1);
            vkCheck(vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
                    "Failed to allocate render command buffer");

            vkCommandBuffer = new VkCommandBuffer(pb.get(0), vkDevice);
        }
    }

    public void beginRecording() {
        beginRecording(null);
    }

    public void beginRecording(InheritanceInfo inheritanceInfo) {
        try (var stack = MemoryStack.stackPush()) {
            var cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            if (oneTimeSubmit) {
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            }
            if (!primary) {
                if (inheritanceInfo == null) {
                    throw new RuntimeException("Secondary buffers must declare inheritance info");
                }
                int numColorFormats = inheritanceInfo.colorFormats.length;
                IntBuffer pColorFormats = stack.callocInt(inheritanceInfo.colorFormats.length);
                for (int i = 0; i < numColorFormats; i++) {
                    pColorFormats.put(0, inheritanceInfo.colorFormats[i]);
                }
                var renderingInfo = VkCommandBufferInheritanceRenderingInfo.calloc(stack)
                        .sType$Default()
                        .depthAttachmentFormat(inheritanceInfo.depthFormat)
                        .pColorAttachmentFormats(pColorFormats)
                        .rasterizationSamples(inheritanceInfo.rasterizationSamples);
                var vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
                        .sType$Default()
                        .pNext(renderingInfo);
                cmdBufInfo.pInheritanceInfo(vkInheritanceInfo);
            }
            vkCheck(vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo), "Failed to begin command buffer");
        }
    }

    public void cleanup(VkCtx vkCtx, CmdPool cmdPool) {
        Logger.trace("Destroying command buffer");
        vkFreeCommandBuffers(vkCtx.getDevice().getVkDevice(), cmdPool.getVkCommandPool(),
                vkCommandBuffer);
    }

    public void endRecording() {
        vkCheck(vkEndCommandBuffer(vkCommandBuffer), "Failed to end command buffer");
    }

    public VkCommandBuffer getVkCommandBuffer() {
        return vkCommandBuffer;
    }

    public void reset() {
        vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
    }

    public void submitAndWait(VkCtx vkCtx, Queue queue) {
        Fence fence = new Fence(vkCtx, true);
        fence.reset(vkCtx);
        try (var stack = MemoryStack.stackPush()) {
            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(vkCommandBuffer);
            queue.submit(cmds, null, null, fence);
        }
        fence.fenceWait(vkCtx);
        fence.cleanup(vkCtx);
    }

    public record InheritanceInfo(int depthFormat, int[] colorFormats, int rasterizationSamples) {
    }
}
