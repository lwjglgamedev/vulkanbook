package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Instance {

    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;

    private static final Logger LOGGER = LogManager.getLogger();

    private final VkInstance vkInstance;
    private VkDebugUtilsMessengerCreateInfoEXT debugUtils;
    private long vkDebugHandle;

    public Instance(boolean validate) {
        LOGGER.debug("Creating Vulkan instance");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create application information
            ByteBuffer appShortName = stack.UTF8("VulkanBook");
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_1);

            // Validation layers
            String[] validationLayers = validate ? getSupportedValidationLayers(stack) : null;
            int numValidationLayers = validationLayers != null ? validationLayers.length : 0;
            boolean supportsValidation = validate;
            if (validate && numValidationLayers == 0) {
                supportsValidation = false;
                LOGGER.warn("Request validation but no supported validation layers found. Falling back to no validation");
            }
            LOGGER.debug("Validation: {}", supportsValidation);

            // Set required  layers
            PointerBuffer requiredLayers = null;
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers);
                for (int i = 0; i < numValidationLayers; i++) {
                    LOGGER.debug("Using validation layer [{}]", validationLayers[i]);
                    requiredLayers.put(i, stack.ASCII(validationLayers[i]));
                }
            }

            // GLFW Extension
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                throw new RuntimeException("Failed to find the GLFW platform surface extensions");
            }

            PointerBuffer requiredExtensions;
            if (supportsValidation) {
                ByteBuffer vkDebugUtilsExtension = stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + 1);
                requiredExtensions.put(glfwExtensions).put(vkDebugUtilsExtension);
            } else {
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining());
                requiredExtensions.put(glfwExtensions);
            }
            requiredExtensions.flip();

            long extension = MemoryUtil.NULL;
            if (supportsValidation) {
                debugUtils = createDebugCallBack();
                extension = debugUtils.address();
            }

            // Create instance info
            VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pNext(extension)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(requiredLayers)
                    .ppEnabledExtensionNames(requiredExtensions);

            PointerBuffer pInstance = stack.mallocPointer(1);
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
            vkInstance = new VkInstance(pInstance.get(0), instanceInfo);

            vkDebugHandle = VK_NULL_HANDLE;
            if (supportsValidation) {
                LongBuffer longBuff = stack.mallocLong(1);
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff), "Error creating debug utils");
                vkDebugHandle = longBuff.get(0);
            }
        }
    }

    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack() {
        VkDebugUtilsMessengerCreateInfoEXT result = VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    Level logLevel = Level.DEBUG;
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                        logLevel = Level.INFO;
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        logLevel = Level.WARN;
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        logLevel = Level.ERROR;
                    }

                    LOGGER.log(logLevel, "VkDebugUtilsCallback, {}", callbackData.pMessageString());
                    return VK_FALSE;
                });
        return result;
    }

    public void cleanup() {
        LOGGER.debug("Destroying Vulkan instance");
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null);
        }
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free();
            debugUtils.free();
        }
        vkDestroyInstance(vkInstance, null);
    }

    private String[] getSupportedValidationLayers(MemoryStack stack) {
        Set<String> supportedLayers = new HashSet<>();
        IntBuffer numLayersArr = stack.callocInt(1);
        vkEnumerateInstanceLayerProperties(numLayersArr, null);
        int numLayers = numLayersArr.get(0);
        LOGGER.debug("Instance supports [{}] layers", numLayers);

        VkLayerProperties.Buffer propsBuf = VkLayerProperties.callocStack(numLayers, stack);
        vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
        for (int i = 0; i < numLayers; i++) {
            VkLayerProperties props = propsBuf.get(i);
            String layerName = props.layerNameString();
            supportedLayers.add(layerName);
            LOGGER.debug("Supported layer [{}]", layerName);
        }

        String[][] validationLayers = new String[][]{
                // Preferred one
                {"VK_LAYER_KHRONOS_validation"},
                // If not available, check LunarG meta layer
                {"VK_LAYER_LUNARG_standard_validation"},
                // If not available, check individual layers
                {
                        "VK_LAYER_GOOGLE_threading",
                        "VK_LAYER_LUNARG_parameter_validation",
                        "VK_LAYER_LUNARG_object_tracker",
                        "VK_LAYER_LUNARG_core_validation",
                        "VK_LAYER_GOOGLE_unique_objects",
                },
                // Last resort
                {"VK_LAYER_LUNARG_core_validation"}
        };

        String[] selectedLayers = null;
        for (String[] layers : validationLayers) {
            boolean supported = true;
            for (String layerName : layers) {
                supported = supported && supportedLayers.contains(layerName);
            }
            if (supported) {
                selectedLayers = layers;
                break;
            }
        }

        return selectedLayers;
    }

    public VkInstance getVkInstance() {
        return vkInstance;
    }
}