package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.vulkanb.eng.graph.vk.*;

import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;

public record TransferBuffer(VkBuffer srcBuffer, VkBuffer dstBuffer) {

    public void recordTransferCommand(CmdBuffer cmd) {
        try (var stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0).dstOffset(0).size(srcBuffer.getRequestedSize());
            vkCmdCopyBuffer(cmd.getVkCommandBuffer(), srcBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
        }
    }
}
