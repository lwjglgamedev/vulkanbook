package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class Queue {

    private final int queueFamilyIndex;
    private final VkQueue vkQueue;

    public Queue(VkCtx vkCtx, int queueFamilyIndex, int queueIndex) {
        Logger.debug("Creating queue");

        this.queueFamilyIndex = queueFamilyIndex;
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(vkCtx.getDevice().getVkDevice(), queueFamilyIndex, queueIndex, pQueue);
            long queue = pQueue.get(0);
            vkQueue = new VkQueue(queue, vkCtx.getDevice().getVkDevice());
        }
    }

    public int getQueueFamilyIndex() {
        return queueFamilyIndex;
    }

    public VkQueue getVkQueue() {
        return vkQueue;
    }

    public void submit(VkCommandBufferSubmitInfo.Buffer commandBuffers, VkSemaphoreSubmitInfo.Buffer waitSemaphores,
                       VkSemaphoreSubmitInfo.Buffer signalSemaphores, Fence fence) {
        try (var stack = MemoryStack.stackPush()) {
            var submitInfo = VkSubmitInfo2.calloc(1, stack)
                    .sType$Default()
                    .pCommandBufferInfos(commandBuffers)
                    .pSignalSemaphoreInfos(signalSemaphores);
            if (waitSemaphores != null) {
                submitInfo.pWaitSemaphoreInfos(waitSemaphores);
            }
            long fenceHandle = fence != null ? fence.getVkFence() : VK_NULL_HANDLE;

            vkCheck(vkQueueSubmit2(vkQueue, submitInfo, fenceHandle), "Failed to submit command to queue");
        }
    }

    public void waitIdle() {
        vkQueueWaitIdle(vkQueue);
    }

    public static class GraphicsQueue extends Queue {

        public GraphicsQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean graphicsQueue = (props.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get graphics Queue family index");
            }
            return index;
        }
    }

    public static class PresentQueue extends Queue {

        public PresentQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getPresentQueueFamilyIndex(vkCtx), queueIndex);
        }

        private static int getPresentQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            try (var stack = MemoryStack.stackPush()) {
                var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
                int numQueuesFamilies = queuePropsBuff.capacity();
                IntBuffer intBuff = stack.mallocInt(1);
                for (int i = 0; i < numQueuesFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkCtx.getPhysDevice().getVkPhysicalDevice(),
                            i, vkCtx.getSurface().getVkSurface(), intBuff);
                    boolean supportsPresentation = intBuff.get(0) == VK_TRUE;
                    if (supportsPresentation) {
                        index = i;
                        break;
                    }
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get Presentation Queue family index");
            }
            return index;
        }
    }
}
