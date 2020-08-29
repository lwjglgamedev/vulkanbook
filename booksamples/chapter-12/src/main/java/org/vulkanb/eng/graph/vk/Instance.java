package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class Instance {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final VkDebugReportCallbackEXT DBG_FUNC = VkDebugReportCallbackEXT.create(
            (flags, objectType, object, location, messageCode, pLayerPrefix, pMessage, pUserData) -> {
                String msg = VkDebugReportCallbackEXT.getString(pMessage);
                Level logLevel = Level.DEBUG;
                if ((flags & EXTDebugReport.VK_DEBUG_REPORT_INFORMATION_BIT_EXT) != 0) {
                    logLevel = Level.INFO;
                } else if ((flags & EXTDebugReport.VK_DEBUG_REPORT_WARNING_BIT_EXT) != 0) {
                    logLevel = Level.WARN;
                } else if ((flags & EXTDebugReport.VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT) != 0) {
                    logLevel = Level.WARN;
                } else if ((flags & EXTDebugReport.VK_DEBUG_REPORT_ERROR_BIT_EXT) != 0) {
                    logLevel = Level.ERROR;
                }

                LOGGER.log(logLevel, "VkDebugReportCallbackEXT, messageCode: [{}],  message: [{}]", messageCode, msg);

                return VK_FALSE;
            }
    );

    private final VkInstance vkInstance;

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
                // Debug extension
                ByteBuffer vkDebugReportExtension = stack.UTF8(EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME);
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + 1);
                requiredExtensions.put(glfwExtensions).put(vkDebugReportExtension);
            } else {
                requiredExtensions = stack.mallocPointer(glfwExtensions.remaining());
                requiredExtensions.put(glfwExtensions);
            }
            requiredExtensions.flip();

            long extension = MemoryUtil.NULL;
            if (supportsValidation) {
                VkDebugReportCallbackCreateInfoEXT dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.callocStack(stack)
                        .sType(EXTDebugReport.VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
                        .flags(EXTDebugReport.VK_DEBUG_REPORT_ERROR_BIT_EXT | EXTDebugReport.VK_DEBUG_REPORT_WARNING_BIT_EXT)
                        .pfnCallback(DBG_FUNC)
                        .pUserData(MemoryUtil.NULL);
                extension = dbgCreateInfo.address();
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
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Vulkan instance");
        vkDestroyInstance(vkInstance, null);
    }

    private String[] getSupportedValidationLayers(MemoryStack stack) {
        Set<String> supportedLayers = new HashSet<>();
        int[] numLayersArr = new int[1];
        vkEnumerateInstanceLayerProperties(numLayersArr, null);
        int numLayers = numLayersArr[0];
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
