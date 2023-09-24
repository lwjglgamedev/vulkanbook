package org.vulkanb.eng.graph.vk

import io.github.oshai.kotlinlogging.KotlinLogging
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK11.*
import org.lwjgl.vulkan.KHRPortabilityEnumeration.VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
import org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck


private val logger = KotlinLogging.logger {}

object Instance {
    private const val MESSAGE_SEVERITY_BITMASK: Int = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT or
            EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
    private const val MESSAGE_TYPE_BITMASK: Int = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
            EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
            EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
    private const val PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration"

    val vkInstance: VkInstance
    private var debugUtils: VkDebugUtilsMessengerCreateInfoEXT? = null
    private var vkDebugHandle: Long = 0
    private var validate = true /* TODO was passed into the constructor */

    init {
        logger.debug { "Creating Vulkan instance" }
        MemoryStack.stackPush().use { stack ->
            // Create application information
            val appShortName = stack.UTF8("VulkanBook")
            val appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(appShortName)
                .applicationVersion(1)
                .pEngineName(appShortName)
                .engineVersion(0)
                .apiVersion(VK_API_VERSION_1_1)

            // Validation layers
            val validationLayers = supportedValidationLayers
            val numValidationLayers = validationLayers.size
            var supportsValidation = validate
            if (validate && numValidationLayers == 0) {
                supportsValidation = false
                logger.warn { "Request validation but no supported validation layers found. Falling back to no validation" }
            }
            logger.debug { "Validation: $supportsValidation" }

            // Set required  layers
            var requiredLayers: PointerBuffer? = null
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers)
                for (i in 0..<numValidationLayers) {
                    logger.debug { "Using validation layer [${validationLayers[i]}]" }
                    requiredLayers.put(i, stack.ASCII(validationLayers[i]))
                }
            }
            val instanceExtensions = instanceExtensions

            // GLFW Extension
            val glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: throw RuntimeException("Failed to find the GLFW platform surface extensions")
            val requiredExtensions: PointerBuffer
            val usePortability = instanceExtensions.contains(PORTABILITY_EXTENSION) &&
                    VulkanUtils.oS == VulkanUtils.OSType.MACOS
            if (supportsValidation) {
                val vkDebugUtilsExtension = stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
                val numExtensions =
                    if (usePortability) glfwExtensions.remaining() + 2 else glfwExtensions.remaining() + 1
                requiredExtensions = stack.mallocPointer(numExtensions)
                requiredExtensions.put(glfwExtensions).put(vkDebugUtilsExtension)
                if (usePortability) {
                    logger.debug { "usePortability: requiredExtension $PORTABILITY_EXTENSION" }
                    requiredExtensions.put(stack.UTF8(PORTABILITY_EXTENSION))
                }
            } else {
                val numExtensions =
                    if (usePortability) glfwExtensions.remaining() + 1 else glfwExtensions.remaining()
                requiredExtensions = stack.mallocPointer(numExtensions)
                requiredExtensions.put(glfwExtensions)
                if (usePortability) {
                    requiredExtensions.put(stack.UTF8(KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME))
                }
            }
            requiredExtensions.flip()
            var extension = MemoryUtil.NULL
            if (supportsValidation) {
                debugUtils = createDebugCallBack()
                    .also { extension = it.address() }
            }

            // Create instance info
            val instanceInfo: VkInstanceCreateInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pNext(extension)
                .pApplicationInfo(appInfo)
                .ppEnabledLayerNames(requiredLayers)
                .ppEnabledExtensionNames(requiredExtensions)
            if (usePortability) {
                instanceInfo.flags(VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR)
            }
            val pInstance = stack.mallocPointer(1)
            vkCreateInstance(instanceInfo, null, pInstance)
                .vkCheck("Error creating instance")
            vkInstance = VkInstance(pInstance.get(0), instanceInfo)
            vkDebugHandle = VK_NULL_HANDLE
            if (supportsValidation) {
                val longBuff = stack.mallocLong(1)
                EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils!!, null, longBuff)
                    .vkCheck("Error creating debug utils")
                vkDebugHandle = longBuff.get(0)
            }
        }
    }

    fun cleanup() {
        logger.debug { "Destroying Vulkan instance" }
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkInstance.let { EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(it, vkDebugHandle, null) }
        }
        vkInstance.let { vkDestroyInstance(it, null) }
        debugUtils?.let {
            it.pfnUserCallback().free()
            it.free()
        }
    }

    private val instanceExtensions: Set<String>
        get() {
            val instanceExtensions: MutableSet<String> = HashSet()
            MemoryStack.stackPush().use { stack ->
                val numExtensionsBuf = stack.callocInt(1)
                vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, null)
                val numExtensions = numExtensionsBuf.get(0)
                logger.debug { "Instance supports [$numExtensions] extensions" }
                val instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack)
                vkEnumerateInstanceExtensionProperties(null as String?, numExtensionsBuf, instanceExtensionsProps)
                for (i in 0..<numExtensions) {
                    val props = instanceExtensionsProps.get(i)
                    val extensionName = props.extensionNameString()
                    instanceExtensions.add(extensionName)
                    logger.debug { "Supported instance extension [$extensionName]" }
                }
            }
            return instanceExtensions
        }
    private val supportedValidationLayers: List<String>
        get() {
            MemoryStack.stackPush().use { stack ->
                val numLayersArr = stack.callocInt(1)
                vkEnumerateInstanceLayerProperties(numLayersArr, null)
                val numLayers = numLayersArr.get(0)
                logger.debug { "Instance supports [$numLayers] layers" }
                val propsBuf = VkLayerProperties.calloc(numLayers, stack)
                vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf)
                val supportedLayers: MutableList<String> = ArrayList()
                 for (i in 0..<numLayers) {
                    val props: VkLayerProperties = propsBuf.get(i)
                    val layerName = props.layerNameString()
                    supportedLayers.add(layerName)
                    logger.debug { "Supported layer [$layerName]" }
                }
                val layersToUse: MutableList<String> = ArrayList()

                // Main validation layer
                if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
                    layersToUse.add("VK_LAYER_KHRONOS_validation")
                    return layersToUse
                }

                // Fallback 1
                if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
                    layersToUse.add("VK_LAYER_LUNARG_standard_validation")
                    return layersToUse
                }

                // Fallback 2 (set)
                val requestedLayers: MutableList<String> = ArrayList()
                requestedLayers.add("VK_LAYER_GOOGLE_threading")
                requestedLayers.add("VK_LAYER_LUNARG_parameter_validation")
                requestedLayers.add("VK_LAYER_LUNARG_object_tracker")
                requestedLayers.add("VK_LAYER_LUNARG_core_validation")
                requestedLayers.add("VK_LAYER_GOOGLE_unique_objects")

                return requestedLayers.filter { supportedLayers.contains(it) }
            }
        }

    private fun createDebugCallBack(): VkDebugUtilsMessengerCreateInfoEXT {
        return VkDebugUtilsMessengerCreateInfoEXT
            .calloc()
            .sType(EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
            .messageSeverity(MESSAGE_SEVERITY_BITMASK)
            .messageType(MESSAGE_TYPE_BITMASK)
            .pfnUserCallback { messageSeverity: Int, messageTypes: Int, pCallbackData: Long, pUserData: Long ->
                val callbackData: VkDebugUtilsMessengerCallbackDataEXT =
                    VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT != 0) {
                    logger.info { "VkDebugUtilsCallback, ${callbackData.pMessageString()}" }
                } else if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT != 0) {
                    logger.warn { "VkDebugUtilsCallback, ${callbackData.pMessageString()}" }
                } else if (messageSeverity and EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT != 0) {
                    logger.error { "VkDebugUtilsCallback, ${callbackData.pMessageString()}" }
                } else {
                    logger.debug { "VkDebugUtilsCallback, ${callbackData.pMessageString()}" }
                }
                VK_FALSE
            }
    }
}