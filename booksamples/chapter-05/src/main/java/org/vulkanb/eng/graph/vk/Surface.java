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
            this.vkSurface = pSurface.get(0);
        }
    }

    public void cleanUp() {
        LOGGER.debug("Destroying Vulkan surface");
        KHRSurface.vkDestroySurfaceKHR(this.physicalDevice.getVkPhysicalDevice().getInstance(), this.vkSurface, null);
    }

    public long getVkSurface() {
        return this.vkSurface;
    }
}
