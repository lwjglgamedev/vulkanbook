package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.wnd.Window;

import java.nio.*;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class SwapChain {

    private final ImageView[] imageViews;
    private final int numImages;
    private final VkExtent2D swapChainExtent;
    private final long vkSwapChain;

    public SwapChain(Window window, Device device, Surface surface, int requestedImages, boolean vsync) {
        Logger.debug("Creating Vulkan SwapChain");
        try (var stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCaps = surface.getSurfaceCaps();

            int reqImages = calcNumImages(surfaceCaps, requestedImages);
            swapChainExtent = calcSwapChainExtent(window, surfaceCaps);

            Surface.SurfaceFormat surfaceFormat = surface.getSurfaceFormat();
            var vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface.getVkSurface())
                    .minImageCount(reqImages)
                    .imageFormat(surfaceFormat.imageFormat())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(swapChainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(surfaceCaps.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .clipped(true);
            if (vsync) {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR);
            } else {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.getVkDevice(), vkSwapchainCreateInfo, null, lp),
                    "Failed to create swap chain");
            vkSwapChain = lp.get(0);

            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat());
            numImages = imageViews.length;
        }
    }

    private static int calcNumImages(VkSurfaceCapabilitiesKHR surfCapabilities, int requestedImages) {
        int maxImages = surfCapabilities.maxImageCount();
        int minImages = surfCapabilities.minImageCount();
        int result = minImages;
        if (maxImages != 0) {
            result = Math.min(requestedImages, maxImages);
        }
        result = Math.max(result, minImages);
        Logger.debug("Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]",
                requestedImages, result, maxImages, minImages);

        return result;
    }

    private static VkExtent2D calcSwapChainExtent(Window window, VkSurfaceCapabilitiesKHR surfCapabilities) {
        var result = VkExtent2D.calloc();
        if (surfCapabilities.currentExtent().width() == 0xFFFFFFFF) {
            // Surface size undefined. Set to the window size if within bounds
            int width = Math.min(window.getWidth(), surfCapabilities.maxImageExtent().width());
            width = Math.max(width, surfCapabilities.minImageExtent().width());

            int height = Math.min(window.getHeight(), surfCapabilities.maxImageExtent().height());
            height = Math.max(height, surfCapabilities.minImageExtent().height());

            result.width(width);
            result.height(height);
        } else {
            // Surface already defined, just use that for the swap chain
            result.set(surfCapabilities.currentExtent());
        }
        return result;
    }

    private static ImageView[] createImageViews(MemoryStack stack, Device device, long swapChain, int format) {
        IntBuffer ip = stack.mallocInt(1);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, null),
                "Failed to get number of surface images");
        int numImages = ip.get(0);

        LongBuffer swapChainImages = stack.mallocLong(numImages);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, swapChainImages),
                "Failed to get surface images");

        var result = new ImageView[numImages];
        var imageViewData = new ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        for (int i = 0; i < numImages; i++) {
            result[i] = new ImageView(device, swapChainImages.get(i), imageViewData);
        }

        return result;
    }

    public int acquireNextImage(Device device, Semaphore imageAqSem) {
        int imageIndex;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            int err = KHRSwapchain.vkAcquireNextImageKHR(device.getVkDevice(), vkSwapChain, ~0L,
                    imageAqSem.getVkSemaphore(), MemoryUtil.NULL, ip);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                return -1;
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swapchain can still be used
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to acquire image: " + err);
            }
            imageIndex = ip.get(0);
        }

        return imageIndex;
    }

    public void cleanup(Device device) {
        Logger.debug("Destroying Vulkan SwapChain");
        swapChainExtent.free();
        Arrays.asList(imageViews).forEach(i -> i.cleanup(device));
        KHRSwapchain.vkDestroySwapchainKHR(device.getVkDevice(), vkSwapChain, null);
    }

    public ImageView getImageView(int pos) {
        return imageViews[pos];
    }

    public int getNumImages() {
        return numImages;
    }

    public VkExtent2D getSwapChainExtent() {
        return swapChainExtent;
    }

    public boolean presentImage(Queue queue, Semaphore renderCompleteSem, int imageIndex) {
        boolean resize = false;
        try (var stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(renderCompleteSem.getVkSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(vkSwapChain))
                    .pImageIndices(stack.ints(imageIndex));

            int err = KHRSwapchain.vkQueuePresentKHR(queue.getVkQueue(), present);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true;
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // Not optimal but swap chain can still be used
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to present KHR: " + err);
            }
        }
        return resize;
    }
}
