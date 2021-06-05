# Vulkan instance

In this chapter we will be having our first taste of Vulkan, we will start by creating a Vulkan instance. This is the very first thing that will be created when dealing with Vulkan. Basically, a Vulkan instance is where all the application state is glued together. In Vulkan there is no global state--all that information is organized around a Vulkan instance.

You can find the complete source code for this chapter [here](../../booksamples/chapter-02).

## Instance first steps

Usually you will have a single Vulkan instance for each application, but the spec allows you to have more. A potential use case for having more than one is if you are using a legacy library that already uses Vulkan (maybe even different version) and do you not want that to interfere with your code. You could then setup a separate instance just for your code. We will start from scratch in this book and, therefore, we will use just a single instance.

This part of the book will show you an integral part of using LWJGL as well as a structure for creating Vulkan objects. The former is the `MemoryStack`, and the latter Vk*Info.

Most of the Vulkan-related code will be placed under the package `org.vulkanb.eng.graph.vk`. In this case, we will create a new class named `Instance` to wrap all the initialization code. So let's start by coding the constructor, which starts like this:

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        LOGGER.debug("Creating Vulkan instance");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ...
        }
        ...
    }
    ...
}
```

### Memory Stack

Before going on, for those who are not familiar with [LWJGL](https://www.lwjgl.org/), we will explain the purpose of the `MemoryStack`. In order to share data with native code, LWJGL uses direct (off-heap) buffers instead of relaying on the [JNI](https://en.wikipedia.org/wiki/Java_Native_Interface) (which would be quite slow). You can think of these buffers as C pointers to a region of memory than can be accessed both by Java and native code. But Java has a major drawback: we cannot allocate objects in the stack, such as in C, even for these direct buffers. This is specially painful for short-lived buffers that may be used to pass some initialization info to native methods.

To solve this, LWJGL comes with the `MemoryStack`, which is basically a direct allocated stack, where we can reserve direct memory when we need to exchange short-lived info with native functions. You can read about all the gory details [here](https://github.com/LWJGL/lwjgl3-wiki/wiki/1.3.-Memory-FAQ). Although is not perfect, it really helps this information sharing, and what is more important, everything allocated in the stack inside a try/catch block is automatically freed (how convenient!) when we exit that block. We will see if [Project Panama](https://openjdk.java.net/projects/panama/) can finally ease the invocation of native code from Java.

*When initializing something in memory, there are two options to choose from: malloc() and calloc(). malloc __does not__ initialize memory (ie set the bits to 0), while calloc does. Malloc is suggested for performance gains, but sometimes calloc is necessary. When using calloc, we do not need to initialize some information fields.*

### Creating Vulkan Structures

Here you can see the basis of all the Vulkan calls. Almost every Vulkan function will use a structure. Of *those*, almost all of them require a type and context-specific attributes. More often than not, it will also require a pointer to creation information. We will go into detail about what these things mean, working backwards.

##### vkCreate\*

This method creates a Vulkan object, returning a status number. It comes in the form of

```java
public static int vkCreate...(VkDevice or VkInstance, Vk...CreateInfo, VkAllocationCallbacks, LongBuffer);
```

This signals to create a Vulkan object, using some things we will set up later. We also use a corresponding CreateInfo struct.

##### Vk\*CreateInfo

When creating objects, we need to define their structure, sometimes with other structures. All structures inherit these types:

- `sType`: The structure type (required).
- `pNext`: Pointer to an extension-specific structure.
- `flags`: Often unused, but intended for specific behaviors. 

##### Vk\*.Buffer & .callocStack()

Sometimes Vulkan expects a list of objects, either with a single element or multiple. In these cases, we have to create a Buffer object, which is a subclass of most objects. Either way, we still have to run a `callocStack` command in order to create the object. For a singular item, use `callocStack(stack)`, and for multiple, use `callocStack(x, stack)`, where x is the amount of items in the list.

Back to the constructor:

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // Create application information
            ByteBuffer appShortName = stack.UTF8("VulkanBook");
            VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_1);
        ...
    }
    ...
}
```

The structure type parameter sType, which, as its names suggests, defines its type. That is, what information it models from. In this case we are defining our application information with the structure `VkApplicationInfo`. As you can see, we cannot create the usual Java objects, but rather we allocate it through the stack and use the following attributes:

- `sType`: Structure type. In this case: `VK_STRUCTURE_TYPE_APPLICATION_INFO`.
- `pApplicationName`: It is basically just some text that will identify the application that uses this instance. In this case we use another helper method from the stack class to create a `ByteBuffer` that points to a null-terminated string.
- `applicationVersion`: The version of our application. You can use the method `VK_VERSION_MAJOR/MINOR/PATCH()` methods to create a more detailed version.
- `pEngineName`: The engine name (as a null-terminated string).
- `engineVersion`: The engine version.
- `apiVersion`: The version of the Vulkan API. This value should be the highest value of the Vulkan version that his application should use encoded according to what is stated in Vulkan specification (major, minor and patch version). In this case we are using version `1.1.0`. If you are using a newer version that has been ported to Java, use that number.

Most of the time these attributes are set to `NULL` and `0` respectively. Since we are allocating the structure in the stack using the `callocStack` stack method, all the memory block associated to it will be initialized with zeros, so we do not need to explicitly set up these common attributes.

## Layers

Vulkan is a layered API. When you read about the Vulkan core, you can think as the mandatory lowest level layer. On top of that, we there are additional layers that will support useful things like validation and debugging information. As said before, Vulkan is a low overhead API, this means that **the driver assumes that you are using the API correctly and does not waste time in performing validations** (error checking is minimal in the core layer). If you want the driver to perform extensive validation you must enable them through specific layers (validations are handled through extension validation layers). While we are developing it is good advice to turn these validation layers on, to check that we are being compliant with the specification. This can be turned off when our application is ready for delivery.

Our `Instance` class constructor receives a boolean parameter indication is validations should be enabled or not. If validation is requested we need first to get the ones that are supported by our driver. 

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // Validation layers
		    List<String> validationLayers = getSupportedValidationLayers();
		    int numValidationLayers = validationLayers.size();
            boolean supportsValidation = validate;
            if (validate && numValidationLayers == 0) {
                supportsValidation = false;
                LOGGER.warn("Request validation but no supported validation layers found. Falling back to no validation");
            }
            LOGGER.debug("Validation: {}", supportsValidation);
        ...
    }
    ...
}
```

We will get the supported validation layers by invoking the `getSupportedValidationLayers`. If we have requested validation, but we have not found any layer that can help on this, we log a warning but continue the execution. In order to use validation layers you need to install the [Vulkan SDK]([Vulkan® SDK - What&#039;s in the SDK - Where to Download](https://www.lunarg.com/vulkan-sdk/)). Let's move out of the constructor code and check the contents of the `getSupportedValidationLayers`:

```java
public class Instance {
    ...
    private List<String> getSupportedValidationLayers() {
    	try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer numLayersArr = stack.callocInt(1);
			vkEnumerateInstanceLayerProperties(numLayersArr, null);
			int numLayers = numLayersArr.get(0);
			LOGGER.debug("Instance supports [{}] layers", numLayers);
        ...
		}
    }
    ...
}
```

We first need to get the number of supported layers, by invoking the `vkEnumerateInstanceLayerProperties` function. This function receives two parameters:

- The number of layers.
- A pointer to a buffer that will hold all the layers properties.

This function can be used to get the total number of supported layers, and to get their properties. We need first to get the number, so the first parameter is an array to get the number of layers, and the second parameter is `null`. In order to get the properties we will need to perform a second call to the same function, once we get the total size. The next fragment shows how we do retrieve these properties to get their name and store them in a `Set`.

```java
public class Instance {
    ...
    private List<String> getSupportedValidationLayers() {
    	try (MemoryStack stack = MemoryStack.stackPush()) {
        ...
			VkLayerProperties.Buffer propsBuf = VkLayerProperties.callocStack(numLayers, stack);
			vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
			List<String> supportedLayers = new ArrayList<>();
			for (int i = 0; i < numLayers; i++) {
				VkLayerProperties props = propsBuf.get(i);
				String layerName = props.layerNameString();
				supportedLayers.add(layerName);
				LOGGER.debug("Supported layer [{}]", layerName);
			}
        ...
		}
    }
    ...
}
```

Once we have the supported layers, we need to select which ones do we want to activate. In order to do so, we construct a checks like this:

```java
public class Instance {
    ...
    private List<String> getSupportedValidationLayers() {
    	try (MemoryStack stack = MemoryStack.stackPush()) {
        ...
			List<String> layersToUse = new ArrayList<>();

			// Main validation layer
			if (supportedLayers.contains("VK_LAYER_KHRONOS_validation")) {
				layersToUse.add("VK_LAYER_KHRONOS_validation");
				return layersToUse;
			}

			// Fallback 1
			if (supportedLayers.contains("VK_LAYER_LUNARG_standard_validation")) {
				layersToUse.add("VK_LAYER_LUNARG_standard_validation");
				return layersToUse;
			}

			// Fallback 2 (set)
			List<String> requestedLayers = new ArrayList<>();
			requestedLayers.add("VK_LAYER_GOOGLE_threading");
			requestedLayers.add("VK_LAYER_LUNARG_parameter_validation");
			requestedLayers.add("VK_LAYER_LUNARG_object_tracker");
			requestedLayers.add("VK_LAYER_LUNARG_core_validation");
			requestedLayers.add("VK_LAYER_GOOGLE_unique_objects");

			List<String> overlap = requestedLayers.stream().filter(requestedLayers::contains).collect(Collectors.toList());

			if (overlap.size() > 0) {
				return overlap;
			}

			// Fallback 3
			if (supportedLayers.contains("VK_LAYER_LUNARG_core_validation")) {
				layersToUse.add("VK_LAYER_LUNARG_core_validation");
				return layersToUse;
			}

			// Returns empty list
			return layersToUse;
        ...
		}
    }
    ...
}
```

We first try to aim for the `VK_LAYER_KHRONOS_validation` meta layer. A meta layer is basically a collection of layers registered under a single name. Then we go down in our priority list combining other meta layers or selecting a list of single layers. We basically check the presence of these preferred layers (ordered by priority) to get the ones that are supported, and pick those.

Let's get back to the constructor. Now we have a list of the names of the supported layers (array of Strings) we need to transform it to a pointer of a list of null terminated Strings:
```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // Set required  layers
            PointerBuffer requiredLayers = null;
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers);
                for (int i = 0; i < numValidationLayers; i++) {
					LOGGER.debug("Using validation layer [{}]", validationLayers.get(i));
					requiredLayers.put(i, stack.ASCII(validationLayers.get(i)));
                }
            }
        ...
    }
    ...
}
```

Now that we've setup all the validation layers, we move on to extensions. Because Vulkan is a cross-platform API, links to windowing systems are handled through extensions. In our case we will be using GLFW, which has extensions for Vulkan, so we need to include this as an extension with the following code:
```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // GLFW Extension
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                throw new RuntimeException("Failed to find the GLFW platform surface extensions");
            }
        ...
    }
    ...
}
```

Depending if we have enabled validation or not we will need to add the extension used for debugging. We will use the `VK_EXT_debug_utils` extension (which should be used instead of older debug extension such as `VK_EXT_debug_report` and `VK_EXT_debug_marker`).
```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
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
        ...
    }
    ...
}
```

Additionally, if we have enabled the debug extension, we will be interested in setting a callback, so we can, for example, log the information reported. We have already enabled the debugging extension, but we need to create it. We also need to pass this extension while creating  the instance to properly log errors while creating and destroying the instance:
```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            long extension = MemoryUtil.NULL;
            if (supportsValidation) {
                debugUtils = createDebugCallBack();
                extension = debugUtils.address();
            }
        ...
    }
    ...
}
```
This is done in a new method named `createDebugCallBack`:
```java
public class Instance {
    ...
    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    ...
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
    ...
}
```
In this method, we instantiate a `VkDebugUtilsMessengerCreateInfoEXT` which is defined by the following attributes:
- `sType`: The type of the structure: `VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT`.
- `messageSeverity`: This will hold a bit mask with the levels of the messages that we are interested in receiving. In our case, we will receive error and warning messages.
- `messageType`: This will hold a bit mask with the types of messages that we are interested in receiving. In our case, we will receive validation and performance messages.
- `pfnUserCallback`:  The function that will be invoked when a message matches the criteria established by the `messageSeverity` and `messageType` fields. In our case, we just log the message with the proper logging level according to the `messageSeverity` parameter.

Finally, going back to the constructor, we have everything we need in order to create the Vulkan instance. In order to do so, we need to setup yet another structure: `VkInstanceCreateInfo`, which is defined as follows:

- Structure type: `VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO`.
- Next extension: In our case, the debug extension configuration structure or `NULL` if no validation is requested or supported.
- The application information structure that we created at the beginning of the chapter.
- The enabled layers.
- The extensions requested.

With that structure, we invoke the `vkCreateInstance` function, and we will get a pointer to the Vulkan instance. We store that address as a long attribute named `vkInstance`.

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
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
            ...
        }
    }
    ...
}
```

The last step is to instantiate the debug extension. We have passed its configuration while creating the Vulkan instance, but this will only be used in the instance creation and destruction phases. For the rest of the code, we will need to instantiate by calling the `vkCreateDebugUtilsMessengerEXT` Vulkan function.
```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            vkDebugHandle = VK_NULL_HANDLE;
            if (supportsValidation) {
                LongBuffer longBuff = stack.mallocLong(1);
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff), "Error creating debug utils");
                vkDebugHandle = longBuff.get(0);
            }
        }
    }
    ...
}
```

Most of the Vulkan functions return an `int` value that is used to check if the call as succeeded or not. To check this, an utility method has been defined in the `VulkanUtils` class that throws a `RuntimeException` if the call does not return `VK_SUCCESS`.

```java
package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK11.VK_SUCCESS;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            throw new RuntimeException(errMsg + ": " + err);
        }
    }
}
```

The `Instance` class provides two additional methods, one for free resources (named `cleanup`) and another one to get the address of the instance (named `getVkInstance`).

```java
public class Instance {
    ...
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
    ...
    public VkInstance getVkInstance() {
        return vkInstance;
    }
}
```

Finally, we can use the Instance class in our `Render` class, in the constructor and `cleanup` methods.
```java
public class Render {
	private final Instance instance;
    ...
    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
    }

    public void cleanup() {
        instance.cleanup();
    }
    ...
}
```

We have added a new configuration variable to control if validation should be used or not:

```java
private EngineProperties() {
...
    private boolean validate;

    private EngineProperties() {
        ...
            validate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
        ...
    }
    ...
    public boolean isValidate() {
        return validate;
    }
```

And that's all! As you can see we have to write lots of code just to setup the Vulkan instance. You can see now why Vulkan is considered an explicit API. A whole chapter passed and we can't even clear the screen. So, contain your expectations, since in the next chapters we will continue writing lots of code required to setup everything. It will take some time in order to draw something, so please be patient. The good news is that when everything is set up, adding incremental features to draw more complex models or to support advanced techniques should require less amount of code. And if we do it correctly, we get a good understanding of Vulkan.

[Next chapter](../chapter-03/chapter-03.md)