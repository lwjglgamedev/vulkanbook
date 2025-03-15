package org.vulkanb.eng.graph.vk;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.wnd.Window;

import java.nio.*;

import static org.lwjgl.vulkan.VK13.VK_FORMAT_B8G8R8A8_SRGB;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class Surface {

    private final VkSurfaceCapabilitiesKHR surfaceCaps;
    private final SurfaceFormat surfaceFormat;
    private final long vkSurface;

    public Surface(Instance instance, PhysDevice physDevice, Window window) {
        Logger.debug("Creating Vulkan surface");
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(instance.getVkInstance(), window.getHandle(),
                    null, pSurface);
            vkSurface = pSurface.get(0);

            surfaceCaps = VkSurfaceCapabilitiesKHR.calloc();
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, surfaceCaps), "Failed to get surface capabilities");

            surfaceFormat = calcSurfaceFormat(physDevice, vkSurface);
        }
    }

    private static SurfaceFormat calcSurfaceFormat(PhysDevice physDevice, long vkSurface) {
        int imageFormat;
        int colorSpace;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, null), "Failed to get the number surface formats");
            int numFormats = ip.get(0);
            if (numFormats <= 0) {
                throw new RuntimeException("No surface formats retrieved");
            }

            var surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, surfaceFormats), "Failed to get surface formats");

            imageFormat = VK_FORMAT_B8G8R8A8_SRGB;
            colorSpace = surfaceFormats.get(0).colorSpace();
            for (int i = 0; i < numFormats; i++) {
                VkSurfaceFormatKHR surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                        surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }
        }
        return new SurfaceFormat(imageFormat, colorSpace);
    }

    public void cleanup(Instance instance) {
        Logger.debug("Destroying Vulkan surface");
        surfaceCaps.free();
        KHRSurface.vkDestroySurfaceKHR(instance.getVkInstance(), vkSurface, null);
    }

    public VkSurfaceCapabilitiesKHR getSurfaceCaps() {
        return surfaceCaps;
    }

    public SurfaceFormat getSurfaceFormat() {
        return surfaceFormat;
    }

    public long getVkSurface() {
        return vkSurface;
    }

    public record SurfaceFormat(int imageFormat, int colorSpace) {
    }
}
