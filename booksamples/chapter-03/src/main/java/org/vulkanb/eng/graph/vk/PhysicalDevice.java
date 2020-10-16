package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class PhysicalDevice {

    private static final Logger LOGGER = LogManager.getLogger();
    private final VkExtensionProperties.Buffer vkDeviceExtensions;
    private final VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    private final VkPhysicalDeviceProperties vkPhysicalDeviceProperties;
    private final VkQueueFamilyProperties.Buffer vkQueueFamilyProps;

    private PhysicalDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            IntBuffer pPropertyCount = stack.mallocInt(1);

            // Get device properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, vkPhysicalDeviceProperties);

            // Get device extensions
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, pPropertyCount, null),
                    "Failed to get number of device extension properties");
            vkDeviceExtensions = VkExtensionProperties.calloc(pPropertyCount.get(0));
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, pPropertyCount, vkDeviceExtensions),
                    "Failed to get extension properties");

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, pPropertyCount, null);
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(pPropertyCount.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, pPropertyCount, vkQueueFamilyProps);

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures);

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties);
        }
    }

    public static PhysicalDevice createPhysicalDevice(Instance instance) {
        LOGGER.debug("Selecting physical devices");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get available devices
            PointerBuffer pPhysicalDevices = getPhysicalDevices(instance, stack);
            int numDevices = pPhysicalDevices.capacity();
            if (numDevices <= 0) {
                throw new RuntimeException("No physical devices found");
            }

            // Populate available devices
            for (int i = 0; i < numDevices; i++) {
                VkPhysicalDevice vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.getVkInstance());
                PhysicalDevice physicalDevice = new PhysicalDevice(vkPhysicalDevice);

                String deviceName = physicalDevice.getDeviceName();
                if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                    LOGGER.debug("Device [{}] supports required extensions", deviceName);
                    LOGGER.debug("Selected device: [{}]", deviceName);
                    return physicalDevice;
                } else {
                    LOGGER.debug("Device [{}] does not support required extensions", deviceName);
                }
                physicalDevice.cleanup();
            }

            throw new RuntimeException("No suitable physical devices found");
        }
    }

    protected static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        // Get number of physical devices
        IntBuffer pPhysicalDeviceCount = stack.mallocInt(1);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, null),
                "Failed to get number of physical devices");
        int numDevices = pPhysicalDeviceCount.get(0);
        LOGGER.debug("Detected {} physical device(s)", numDevices);

        // Populate physical devices list pointer
        PointerBuffer pPhysicalDevices = stack.mallocPointer(numDevices);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), pPhysicalDeviceCount, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }

    public void cleanup() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Destroying physical device [{}]", vkPhysicalDeviceProperties.deviceNameString());
        }
        vkMemoryProperties.free();
        vkPhysicalDeviceFeatures.free();
        vkQueueFamilyProps.free();
        vkDeviceExtensions.free();
        vkPhysicalDeviceProperties.free();
    }

    public String getDeviceName() {
        return vkPhysicalDeviceProperties.deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return vkMemoryProperties;
    }

    public VkPhysicalDevice getVkPhysicalDevice() {
        return vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties getVkPhysicalDeviceProperties() {
        return vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return vkQueueFamilyProps;
    }

    private boolean hasGraphicsQueueFamily() {
        if (vkQueueFamilyProps != null) {
            for (int i = 0; i < vkQueueFamilyProps.capacity(); i++) {
                VkQueueFamilyProperties familyProps = vkQueueFamilyProps.get(i);
                if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasKHRSwapChainExtension() {
        if (vkDeviceExtensions != null) {
            for (int i = 0; i < vkDeviceExtensions.capacity(); i++) {
                String extensionName = vkDeviceExtensions.get(i).extensionNameString();
                if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}