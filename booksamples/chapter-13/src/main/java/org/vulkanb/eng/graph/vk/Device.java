package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.FloatBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Device {

    private static final Logger LOGGER = LogManager.getLogger();
    private MemoryAllocator memoryAllocator;
    private PhysicalDevice physicalDevice;
    private boolean samplerAnisotropy;
    private VkDevice vkDevice;

    public Device(Instance instance, PhysicalDevice physicalDevice) {
        LOGGER.debug("Creating device");

        this.physicalDevice = physicalDevice;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Define required extensions
            PointerBuffer requiredExtensions = stack.mallocPointer(1);
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));

            // Set up required features
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.callocStack(stack);
            VkPhysicalDeviceFeatures supportedFeatures = this.physicalDevice.getVkPhysicalDeviceFeatures();
            samplerAnisotropy = supportedFeatures.samplerAnisotropy();
            if (samplerAnisotropy) {
                features.samplerAnisotropy(true);
            }
            features.depthClamp(supportedFeatures.depthClamp());

            // Enable all the queue families
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            VkDeviceQueueCreateInfo.Buffer queueCreationInfoBuf = VkDeviceQueueCreateInfo.callocStack(numQueuesFamilies, stack);
            for (int i = 0; i < numQueuesFamilies; i++) {
                FloatBuffer priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfoBuf.get(i)
                        .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .ppEnabledExtensionNames(requiredExtensions)
                    .pEnabledFeatures(features)
                    .pQueueCreateInfos(queueCreationInfoBuf);

            PointerBuffer pp = stack.mallocPointer(1);
            vkCheck(vkCreateDevice(physicalDevice.getVkPhysicalDevice(), deviceCreateInfo, null, pp),
                    "Failed to create device");
            vkDevice = new VkDevice(pp.get(0), physicalDevice.getVkPhysicalDevice(), deviceCreateInfo);

            memoryAllocator = new MemoryAllocator(instance, physicalDevice, vkDevice);
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Vulkan device");
        memoryAllocator.cleanUp();
        vkDestroyDevice(vkDevice, null);
    }

    public MemoryAllocator getMemoryAllocator() {
        return memoryAllocator;
    }

    public PhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }

    public VkDevice getVkDevice() {
        return vkDevice;
    }

    public boolean isSamplerAnisotropy() {
        return samplerAnisotropy;
    }

    public void waitIdle() {
        vkDeviceWaitIdle(vkDevice);
    }
}
