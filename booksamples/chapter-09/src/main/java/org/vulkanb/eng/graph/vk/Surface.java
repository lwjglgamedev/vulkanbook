package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;

import java.nio.LongBuffer;

public class Surface {

    private static final Logger LOGGER = LogManager.getLogger();
    private PhysicalDevice physicalDevice;
    private long vkSurface;

    public Surface(PhysicalDevice physicalDevice, long windowHandle) {
        LOGGER.debug("Creating Vulkan surface");
        this.physicalDevice = physicalDevice;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(this.physicalDevice.getVkPhysicalDevice().getInstance(), windowHandle,
                    null, pSurface);
            vkSurface = pSurface.get(0);
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Vulkan surface");
        KHRSurface.vkDestroySurfaceKHR(physicalDevice.getVkPhysicalDevice().getInstance(), vkSurface, null);
    }

    public long getVkSurface() {
        return vkSurface;
    }
}
