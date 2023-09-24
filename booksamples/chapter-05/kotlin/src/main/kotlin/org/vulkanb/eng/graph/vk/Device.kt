package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.VkDevice
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

private val logger = KotlinLogging.logger {}

class Device(physicalDevice: PhysicalDevice) {
    val physicalDevice: PhysicalDevice
    val vkDevice: VkDevice

    init {
        logger.debug { "Creating device" }
        this.physicalDevice = physicalDevice
        MemoryStack.stackPush().use { stack ->

            // Define required extensions
            val deviceExtensions = deviceExtensions
            val usePortability =
                deviceExtensions.contains(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VulkanUtils.oS == VulkanUtils.OSType.MACOS
            val numExtensions = if (usePortability) 2 else 1
            val requiredExtensions: PointerBuffer = stack.mallocPointer(numExtensions)
            requiredExtensions.put(stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            if (usePortability) {
                requiredExtensions.put(stack.ASCII(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
            }
            requiredExtensions.flip()

            // Set up required features
            val features = VkPhysicalDeviceFeatures.calloc(stack)

            // Enable all the queue families
            val queuePropsBuff = physicalDevice.vkQueueFamilyProps
            val numQueuesFamilies = queuePropsBuff.capacity()
            val queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack)
            for (i in 0..<numQueuesFamilies) {
                val priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount())
                queueCreationInfoBuf.get(i)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(i)
                    .pQueuePriorities(priorities)
            }

            val deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .ppEnabledExtensionNames(requiredExtensions)
                .pEnabledFeatures(features)
                .pQueueCreateInfos(queueCreationInfoBuf)

            val pp = stack.mallocPointer(1)
            vkCreateDevice(physicalDevice.vkPhysicalDevice, deviceCreateInfo, null, pp)
                .vkCheck("Failed to create device")
            vkDevice = VkDevice(pp.get(0), physicalDevice.vkPhysicalDevice, deviceCreateInfo)
        }
    }

    fun cleanup() {
        logger.debug { "Destroying Vulkan device" }
        vkDestroyDevice(vkDevice, null)
    }

    private val deviceExtensions: Set<String>
        get() {
            val deviceExtensions: MutableSet<String> = HashSet()
            MemoryStack.stackPush().use { stack ->
                val numExtensionsBuf = stack.callocInt(1)
                vkEnumerateDeviceExtensionProperties(
                    physicalDevice.vkPhysicalDevice,
                    null as String?,
                    numExtensionsBuf,
                    null
                )
                val numExtensions = numExtensionsBuf.get(0)
                logger.debug { "Device supports [$numExtensions] extensions" }
                val propsBuff = VkExtensionProperties.calloc(numExtensions, stack)
                vkEnumerateDeviceExtensionProperties(
                    physicalDevice.vkPhysicalDevice,
                    null as String?,
                    numExtensionsBuf,
                    propsBuff
                )
                for (i in 0..<numExtensions) {
                    val props = propsBuff.get(i)
                    val extensionName = props.extensionNameString()
                    deviceExtensions.add(extensionName)
                    logger.debug { "Supported device extension [$extensionName]" }
                }
            }
            return deviceExtensions
        }

    fun waitIdle() {
        vkDeviceWaitIdle(vkDevice)
    }
}
