package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.Window;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class SwapChain {

    private final Device device;
    private final ImageView[] imageViews;
    private final SurfaceFormat surfaceFormat;
    private final VkExtent2D swapChainExtent;
    private final SyncSemaphores[] syncSemaphoresList;
    private final long vkSwapChain;

    private int currentFrame;

    public SwapChain(Device device, Surface surface, Window window, int requestedImages, boolean vsync,
                     Queue.PresentQueue presentationQueue, Queue[] concurrentQueues) {
        Logger.debug("Creating Vulkan SwapChain");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            PhysicalDevice physicalDevice = device.getPhysicalDevice();

            // Get surface capabilities
            VkSurfaceCapabilitiesKHR surfCapabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.getPhysicalDevice().getVkPhysicalDevice(),
                    surface.getVkSurface(), surfCapabilities), "Failed to get surface capabilities");

            int numImages = calcNumImages(surfCapabilities, requestedImages);

            surfaceFormat = calcSurfaceFormat(physicalDevice, surface);

            swapChainExtent = calcSwapChainExtent(window, surfCapabilities);

            VkSwapchainCreateInfoKHR vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                    .surface(surface.getVkSurface())
                    .minImageCount(numImages)
                    .imageFormat(surfaceFormat.imageFormat())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(swapChainExtent)
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .preTransform(surfCapabilities.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .clipped(true);
            if (vsync) {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR);
            } else {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
            }

            int numQueues = concurrentQueues != null ? concurrentQueues.length : 0;
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < numQueues; i++) {
                Queue queue = concurrentQueues[i];
                if (queue.getQueueFamilyIndex() != presentationQueue.getQueueFamilyIndex()) {
                    indices.add(queue.getQueueFamilyIndex());
                }
            }
            if (indices.size() > 0) {
                IntBuffer intBuffer = stack.mallocInt(indices.size() + 1);
                indices.forEach(i -> intBuffer.put(i));
                intBuffer.put(presentationQueue.getQueueFamilyIndex()).flip();
                vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                        .queueFamilyIndexCount(intBuffer.capacity())
                        .pQueueFamilyIndices(intBuffer);
            } else {
                vkSwapchainCreateInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.getVkDevice(), vkSwapchainCreateInfo, null, lp),
                    "Failed to create swap chain");
            vkSwapChain = lp.get(0);

            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat);
            numImages = imageViews.length;
            syncSemaphoresList = new SyncSemaphores[numImages];
            Arrays.setAll(syncSemaphoresList, i -> new SyncSemaphores(device));
            currentFrame = 0;
        }
    }

    public int acquireNextImage() {
        int imageIndex;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            int err = KHRSwapchain.vkAcquireNextImageKHR(device.getVkDevice(), vkSwapChain, ~0L,
                    syncSemaphoresList[currentFrame].imgAcquisitionSemaphore().getVkSemaphore(), MemoryUtil.NULL, ip);
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

    private int calcNumImages(VkSurfaceCapabilitiesKHR surfCapabilities, int requestedImages) {
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

    private SurfaceFormat calcSurfaceFormat(PhysicalDevice physicalDevice, Surface surface) {
        int imageFormat;
        int colorSpace;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            IntBuffer ip = stack.mallocInt(1);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.getVkPhysicalDevice(),
                    surface.getVkSurface(), ip, null), "Failed to get the number surface formats");
            int numFormats = ip.get(0);
            if (numFormats <= 0) {
                throw new RuntimeException("No surface formats retrieved");
            }

            VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.getVkPhysicalDevice(),
                    surface.getVkSurface(), ip, surfaceFormats), "Failed to get surface formats");

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

    private VkExtent2D calcSwapChainExtent(Window window, VkSurfaceCapabilitiesKHR surfCapabilities) {
        VkExtent2D result = VkExtent2D.calloc();
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

    public void cleanup() {
        Logger.debug("Destroying Vulkan SwapChain");
        swapChainExtent.free();
        Arrays.asList(imageViews).forEach(ImageView::cleanup);
        Arrays.asList(syncSemaphoresList).forEach(SyncSemaphores::cleanup);
        KHRSwapchain.vkDestroySwapchainKHR(device.getVkDevice(), vkSwapChain, null);
    }

    private ImageView[] createImageViews(MemoryStack stack, Device device, long swapChain, int format) {
        ImageView[] result;

        IntBuffer ip = stack.mallocInt(1);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, null),
                "Failed to get number of surface images");
        int numImages = ip.get(0);

        LongBuffer swapChainImages = stack.mallocLong(numImages);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, swapChainImages),
                "Failed to get surface images");

        result = new ImageView[numImages];
        ImageView.ImageViewData imageViewData = new ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        for (int i = 0; i < numImages; i++) {
            result[i] = new ImageView(device, swapChainImages.get(i), imageViewData);
        }

        return result;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public Device getDevice() {
        return device;
    }

    public ImageView[] getImageViews() {
        return imageViews;
    }

    public int getNumImages() {
        return imageViews.length;
    }

    public SurfaceFormat getSurfaceFormat() {
        return surfaceFormat;
    }

    public VkExtent2D getSwapChainExtent() {
        return swapChainExtent;
    }

    public SyncSemaphores[] getSyncSemaphoresList() {
        return syncSemaphoresList;
    }

    public long getVkSwapChain() {
        return vkSwapChain;
    }

    public boolean presentImage(Queue queue, int imageIndex) {
        boolean resize = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(
                            syncSemaphoresList[currentFrame].renderCompleteSemaphore().getVkSemaphore()))
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
        currentFrame = (currentFrame + 1) % imageViews.length;
        return resize;
    }

    public record SurfaceFormat(int imageFormat, int colorSpace) {
    }

    public record SyncSemaphores(Semaphore imgAcquisitionSemaphore, Semaphore geometryCompleteSemaphore,
                                 Semaphore renderCompleteSemaphore) {

        public SyncSemaphores(Device device) {
            this(new Semaphore(device), new Semaphore(device), new Semaphore(device));
        }

        public void cleanup() {
            imgAcquisitionSemaphore.cleanup();
            geometryCompleteSemaphore.cleanup();
            renderCompleteSemaphore.cleanup();
        }
    }
}