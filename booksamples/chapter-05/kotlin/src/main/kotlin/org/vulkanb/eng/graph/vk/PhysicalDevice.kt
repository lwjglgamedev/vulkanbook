package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck

private val logger = KotlinLogging.logger {}

open class PhysicalDevice private constructor(val vkPhysicalDevice: VkPhysicalDevice) {
    val vkDeviceExtensions: VkExtensionProperties.Buffer
    val vkMemoryProperties: VkPhysicalDeviceMemoryProperties
    val vkPhysicalDeviceFeatures: VkPhysicalDeviceFeatures
    val vkPhysicalDeviceProperties: VkPhysicalDeviceProperties
    val vkQueueFamilyProps: VkQueueFamilyProperties.Buffer

    init {
        MemoryStack.stackPush().use { stack ->
            val intBuffer = stack.mallocInt(1)

            // Get device properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()
            vkGetPhysicalDeviceProperties(vkPhysicalDevice, vkPhysicalDeviceProperties)

            // Get device extensions
            vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, null)
                .vkCheck("Failed to get number of device extension properties")

            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0))
            vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, null as String?, intBuffer, vkDeviceExtensions)
                .vkCheck("Failed to get extension properties")

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null)
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0))
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps)
            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures)

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties)
        }
    }

    fun cleanup() {
        logger.debug { "Destroying physical device [${vkPhysicalDeviceProperties.deviceNameString()}]" }
        vkMemoryProperties.free()
        vkPhysicalDeviceFeatures.free()
        vkQueueFamilyProps.free()
        vkDeviceExtensions.free()
        vkPhysicalDeviceProperties.free()
    }

    val deviceName: String
        get() = vkPhysicalDeviceProperties.deviceNameString()

    private fun hasGraphicsQueueFamily(): Boolean {
        var result = false
        val numQueueFamilies = vkQueueFamilyProps.capacity()
        for (i in 0..<numQueueFamilies) {
            val familyProps = vkQueueFamilyProps.get(i)
            if (familyProps.queueFlags() and VK_QUEUE_GRAPHICS_BIT != 0) {
                result = true
                break
            }
        }
        return result
    }

    private fun hasKHRSwapChainExtension(): Boolean {
        var result = false
        val numExtensions = vkDeviceExtensions.capacity()
        for (i in 0..<numExtensions) {
            val extensionName = vkDeviceExtensions.get(i).extensionNameString()
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME == extensionName) {
                result = true
                break
            }
        }
        return result
    }

    companion object {
        fun createPhysicalDevice(preferredDeviceName: String?): PhysicalDevice {
            logger.debug { "Selecting physical devices" }
            var selectedPhysicalDevice: PhysicalDevice? = null
            MemoryStack.stackPush().use { stack ->
                // Get available devices
                val pPhysicalDevices = getPhysicalDevices(stack)
                val numDevices = pPhysicalDevices.capacity()
                if (numDevices <= 0) {
                    throw RuntimeException("No physical devices found")
                }

                // Populate available devices
                val devices: MutableList<PhysicalDevice> = ArrayList()
                for (i in 0..<numDevices) {
                    val vkPhysicalDevice = VkPhysicalDevice(pPhysicalDevices.get(i), Instance.vkInstance)
                    val physicalDevice = PhysicalDevice(vkPhysicalDevice)
                    val deviceName = physicalDevice.deviceName
                    if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                        logger.debug { "Device [$deviceName] supports required extensions" }
                        if (preferredDeviceName != null && preferredDeviceName == deviceName) {
                            selectedPhysicalDevice = physicalDevice
                            break
                        }
                        devices.add(physicalDevice)
                    } else {
                        logger.debug { "Device [$deviceName] does not support required extensions" }
                        physicalDevice.cleanup()
                    }
                }

                // No preferred device or it does not meet requirements, just pick the first one
                selectedPhysicalDevice =
                    if (selectedPhysicalDevice == null && devices.isNotEmpty()) devices.removeAt(0) else selectedPhysicalDevice

                // Clean up non-selected devices
                for (physicalDevice in devices) {
                    physicalDevice.cleanup()
                }
                if (selectedPhysicalDevice == null) {
                    throw RuntimeException("No suitable physical devices found")
                }
                logger.debug { "Selected device: [${selectedPhysicalDevice!!.deviceName}]" }
            }
            return selectedPhysicalDevice!!
        }

        protected fun getPhysicalDevices(stack: MemoryStack): PointerBuffer {
            val pPhysicalDevices: PointerBuffer
            // Get number of physical devices
            val intBuffer = stack.mallocInt(1)
            vkEnumeratePhysicalDevices(Instance.vkInstance, intBuffer, null)
                .vkCheck("Failed to get number of physical devices")

            val numDevices = intBuffer.get(0)
            logger.debug { "Detected {$numDevices} physical device(s)" }

            // Populate physical devices list pointer
            pPhysicalDevices = stack.mallocPointer(numDevices)
            vkEnumeratePhysicalDevices(Instance.vkInstance, intBuffer, pPhysicalDevices)
                .vkCheck("Failed to get physical devices")

            return pPhysicalDevices
        }
    }
}