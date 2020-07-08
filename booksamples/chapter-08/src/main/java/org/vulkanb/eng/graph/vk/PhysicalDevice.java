package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class PhysicalDevice {

    private static final Logger LOGGER = LogManager.getLogger();
    private VkExtensionProperties.Buffer vkDeviceExtensions;
    private VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    private VkPhysicalDevice vkPhysicalDevice;
    private VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    private VkPhysicalDeviceProperties vkPhysicalDeviceProperties;
    private VkQueueFamilyProperties.Buffer vkQueueFamilyProps;

    private PhysicalDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            IntBuffer intBuffer = stack.mallocInt(1);

            // Get device properties
            this.vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(this.vkPhysicalDevice, this.vkPhysicalDeviceProperties);

            // Get device extensions
            vkCheck(vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (String) null, intBuffer, null),
                    "Failed to get number of device extension properties");
            this.vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0));
            vkCheck(vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (String) null, intBuffer, this.vkDeviceExtensions),
                    "Failed to get extension properties");

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, intBuffer, null);
            this.vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, intBuffer, this.vkQueueFamilyProps);

            this.vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(this.vkPhysicalDevice, this.vkPhysicalDeviceFeatures);

            // Get Memory information and properties
            this.vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(this.vkPhysicalDevice, this.vkMemoryProperties);
        }
    }

    public static PhysicalDevice createPhysicalDevice(Instance instance, String prefferredDeviceName) {
        LOGGER.debug("Selecting physical devices");
        PhysicalDevice selectedPhysicalDevice = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get available devices
            PointerBuffer pPhysicalDevices = getPhysicalDevices(instance, stack);
            int numDevices = pPhysicalDevices != null ? pPhysicalDevices.capacity() : 0;
            if (numDevices <= 0) {
                throw new RuntimeException("No physical devices found");
            }

            // Populate available devices
            List<PhysicalDevice> devices = new ArrayList<>();
            for (int i = 0; i < numDevices; i++) {
                VkPhysicalDevice vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.getVkInstance());
                PhysicalDevice physicalDevice = new PhysicalDevice(vkPhysicalDevice);

                String deviceName = physicalDevice.getDeviceName();
                if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                    LOGGER.debug("Device [{}] supports required extensions", deviceName);
                    if (prefferredDeviceName != null && prefferredDeviceName.equals(deviceName)) {
                        selectedPhysicalDevice = physicalDevice;
                        break;
                    }
                    devices.add(physicalDevice);
                } else {
                    LOGGER.debug("Device [{}] does not support required extensions", deviceName);
                    physicalDevice.cleanUp();
                }
            }

            // No preferred device or it does not meet requirements, just pick the first one
            selectedPhysicalDevice = selectedPhysicalDevice == null && !devices.isEmpty() ? devices.remove(0) : selectedPhysicalDevice;

            // Clean up non-selected devices
            for (PhysicalDevice physicalDevice : devices) {
                physicalDevice.cleanUp();
            }

            if (selectedPhysicalDevice == null) {
                throw new RuntimeException("No suitable physical devices found");
            }
            LOGGER.debug("Selected device: [{}]", selectedPhysicalDevice.getDeviceName());
        }

        return selectedPhysicalDevice;
    }

    protected static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        PointerBuffer pPhysicalDevices;
        // Get number of physical devices
        IntBuffer intBuffer = stack.mallocInt(1);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, null),
                "Failed to get number of physical devices");
        int numDevices = intBuffer.get(0);
        LOGGER.debug("Detected {} physical device(s)", numDevices);

        // Populate physical devices list pointer
        pPhysicalDevices = stack.mallocPointer(numDevices);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }

    public void cleanUp() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Destroying physical device [{}]", this.vkPhysicalDeviceProperties.deviceNameString());
        }
        this.vkMemoryProperties.free();
        this.vkPhysicalDeviceFeatures.free();
        this.vkQueueFamilyProps.free();
        this.vkDeviceExtensions.free();
        this.vkPhysicalDeviceProperties.free();
    }

    public String getDeviceName() {
        return this.vkPhysicalDeviceProperties.deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return this.vkMemoryProperties;
    }

    public VkPhysicalDevice getVkPhysicalDevice() {
        return this.vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return this.vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties getVkPhysicalDeviceProperties() {
        return this.vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return this.vkQueueFamilyProps;
    }

    private boolean hasGraphicsQueueFamily() {
        boolean result = false;
        int numQueueFamilies = this.vkQueueFamilyProps != null ? vkQueueFamilyProps.capacity() : 0;
        for (int i = 0; i < numQueueFamilies; i++) {
            VkQueueFamilyProperties familyProps = this.vkQueueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean hasKHRSwapChainExtension() {
        boolean result = false;
        int numExtensions = this.vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = this.vkDeviceExtensions.get(i).extensionNameString();
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensionName)) {
                result = true;
                break;
            }
        }
        return result;
    }
}