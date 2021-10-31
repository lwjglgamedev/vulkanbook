package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Queue {

    private final int queueFamilyIndex;
    private final VkQueue vkQueue;

    public Queue(Device device, int queueFamilyIndex, int queueIndex) {
        Logger.debug("Creating queue");

        this.queueFamilyIndex = queueFamilyIndex;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device.getVkDevice(), queueFamilyIndex, queueIndex, pQueue);
            long queue = pQueue.get(0);
            vkQueue = new VkQueue(queue, device.getVkDevice());
        }
    }

    public int getQueueFamilyIndex() {
        return queueFamilyIndex;
    }

    public VkQueue getVkQueue() {
        return vkQueue;
    }

    public void submit(PointerBuffer commandBuffers, LongBuffer waitSemaphores, IntBuffer dstStageMasks,
                       LongBuffer signalSemaphores, Fence fence) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            if (waitSemaphores != null) {
                submitInfo.waitSemaphoreCount(waitSemaphores.capacity())
                        .pWaitSemaphores(waitSemaphores)
                        .pWaitDstStageMask(dstStageMasks);
            } else {
                submitInfo.waitSemaphoreCount(0);
            }
            long fenceHandle = fence != null ? fence.getVkFence() : VK_NULL_HANDLE;

            vkCheck(vkQueueSubmit(vkQueue, submitInfo, fenceHandle),
                    "Failed to submit command to queue");
        }
    }

    public void waitIdle() {
        vkQueueWaitIdle(vkQueue);
    }

    public static class ComputeQueue extends Queue {

        public ComputeQueue(Device device, int queueIndex) {
            super(device, getComputeQueueFamilyIndex(device), queueIndex);
        }

        private static int getComputeQueueFamilyIndex(Device device) {
            int index = -1;
            PhysicalDevice physicalDevice = device.getPhysicalDevice();
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean computeQueue = (props.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
                if (computeQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get compute Queue family index");
            }
            return index;
        }
    }

    public static class GraphicsQueue extends Queue {

        public GraphicsQueue(Device device, int queueIndex) {
            super(device, getGraphicsQueueFamilyIndex(device), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(Device device) {
            int index = -1;
            PhysicalDevice physicalDevice = device.getPhysicalDevice();
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
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

        public PresentQueue(Device device, Surface surface, int queueIndex) {
            super(device, getPresentQueueFamilyIndex(device, surface), queueIndex);
        }

        private static int getPresentQueueFamilyIndex(Device device, Surface surface) {
            int index = -1;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PhysicalDevice physicalDevice = device.getPhysicalDevice();
                VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
                int numQueuesFamilies = queuePropsBuff.capacity();
                IntBuffer intBuff = stack.mallocInt(1);
                for (int i = 0; i < numQueuesFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice.getVkPhysicalDevice(),
                            i, surface.getVkSurface(), intBuff);
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
