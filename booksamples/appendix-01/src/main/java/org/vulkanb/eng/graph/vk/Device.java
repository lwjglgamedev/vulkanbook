package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.EngineProperties;

import java.nio.FloatBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Device {

    private final MemoryAllocator memoryAllocator;
    private final PhysicalDevice physicalDevice;
    private final boolean samplerAnisotropy;
    private final VkDevice vkDevice;

    public Device(Instance instance, PhysicalDevice physicalDevice) {
        Logger.debug("Creating device");

        this.physicalDevice = physicalDevice;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            // Define required extensions
            EngineProperties engineProperties = EngineProperties.getInstance();
            boolean enableCheckPoints = engineProperties.isEnableCheckPoints();
            PhysicalDevice.CheckPointExtension checkPointExtension = physicalDevice.getCheckPointExtension();
            if (enableCheckPoints && checkPointExtension == PhysicalDevice.CheckPointExtension.NONE) {
                Logger.warn("Requested check point extensions but not supported by device");
                enableCheckPoints = false;
            }
            int numRequiredExtensions = enableCheckPoints ? 2 : 1;
            PointerBuffer requiredExtensions = stack.mallocPointer(numRequiredExtensions);
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            if (enableCheckPoints) {
                if (checkPointExtension == PhysicalDevice.CheckPointExtension.NVIDIA) {
                    requiredExtensions.put(1, stack.ASCII(NVDeviceDiagnosticCheckpoints.VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME));
                } else {
                    requiredExtensions.put(1, stack.ASCII(AMDBufferMarker.VK_AMD_BUFFER_MARKER_EXTENSION_NAME));
                }
            }

            // Set up required features
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
            VkPhysicalDeviceFeatures supportedFeatures = this.physicalDevice.getVkPhysicalDeviceFeatures();
            samplerAnisotropy = supportedFeatures.samplerAnisotropy();
            if (samplerAnisotropy) {
                features.samplerAnisotropy(true);
            }
            features.depthClamp(supportedFeatures.depthClamp());
            features.geometryShader(true);
            if (!supportedFeatures.multiDrawIndirect()) {
                throw new RuntimeException("Multi draw Indirect not supported");
            }
            features.multiDrawIndirect(true);

            // Enable all the queue families
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            VkDeviceQueueCreateInfo.Buffer queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack);
            for (int i = 0; i < numQueuesFamilies; i++) {
                FloatBuffer priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfoBuf.get(i)
                        .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }

            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
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
        Logger.debug("Destroying Vulkan device");
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
