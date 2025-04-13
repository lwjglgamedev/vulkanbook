# Chapter 02 - Vulkan Instance

In this chapter, we will be having our first taste of Vulkan, we will start by creating a Vulkan instance.
This is the very first thing that will be created when dealing with Vulkan.
Basically, a Vulkan instance is where all the application state is glued together.
In Vulkan, there is no global state--all that information is organized around a Vulkan instance.

You can find the complete source code for this chapter [here](../../booksamples/chapter-02).

## Instance first steps

Usually you will have a single Vulkan instance for each application, but the spec allows you to have more.
A potential use case for having more than one is if you are using a legacy library that already uses Vulkan
(maybe even different version) and do you not want that to interfere with your code.
You could then set up a separate instance just for your code.
We will start from scratch in this book and, therefore, we will use just a single instance.

This part of the book will show you an integral part of using LWJGL as well as a structure for creating Vulkan objects.
The former is the `MemoryStack`, and the latter Vk*Info.

Most of the Vulkan-related code will be placed under the package `org.vulkanb.eng.graph.vk`.
In this case, we will create a new class named `Instance` to wrap all the initialization code.
So let's start by coding the constructor, which starts like this:

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        Logger.debug("Creating Vulkan instance");
        try (var stack = MemoryStack.stackPush()) {
            ...
        }
        ...
    }
    ...
}
```

### Memory Stack

Before going on, for those who are not familiar with [LWJGL](https://www.lwjgl.org/), we will explain the purpose of the `MemoryStack`.
To share data with native code,
LWJGL uses direct (off-heap) buffers instead of relaying on the [JNI](https://en.wikipedia.org/wiki/Java_Native_Interface)
(which would be quite slow).
You can think of these buffers as C pointers to a region of memory than can be accessed both by Java and native code.
But Java has a major drawback: we cannot allocate objects in the stack, such as in C, even for these direct buffers.
This is specially painful for short-lived buffers that may be used to pass some initialization info to native methods.

To solve this, LWJGL comes with the `MemoryStack`, which is basically a direct allocated stack,
where we can reserve direct memory when we need to exchange short-lived info with native functions.
You can read about all the gory details [here](https://github.com/LWJGL/lwjgl3-wiki/wiki/1.3.-Memory-FAQ).
Although it is not perfect, it really helps with this information sharing, and what is more important,
everything allocated in the stack inside a try/catch block is automatically freed (how convenient!) when we exit that block.
We will see if [Project Panama](https://openjdk.java.net/projects/panama/) can finally ease the invocation of native code from Java.

*When initializing something in memory, there are two options to choose from: `malloc()` and `calloc()`.
`malloc` __does not__ initialize memory (i.e., set the bits to 0), while calloc does.
`malloc` is suggested for performance gains, but sometimes calloc is necessary.
When using calloc, we do not need to initialize some information fields.*

### Creating Vulkan Structures

Here you can see the basis of all the Vulkan calls.
Almost every Vulkan function will use a structure.
Of *those*, almost all of them require a type and context-specific attributes.
More often than not, it will also require a pointer to creation information.
We will go into detail about what these things mean, working backwards.

##### vkCreate\*

This method creates a Vulkan object, returning a status number. It comes in the form of

```java
public static int vkCreate...(VkDevice or VkInstance, Vk...CreateInfo, VkAllocationCallbacks, LongBuffer);
```

This signals to create a Vulkan object, using some things we will set up later. We also use a corresponding CreateInfo struct.

##### Vk\*CreateInfo

When creating objects, we need to define their structure, sometimes with other structures. All structures inherit these types:

- `sType`: The structure type (required).
  Although this is required in regular C/C++ Code, LWJGL provides a convenience method to set it automatically, named `sType$Default`.
  This method does ot require the `sType` field to be set. We will use this convenience method in this book.
- `pNext`: Pointer to an extension-specific structure.
- `flags`: Often unused, but intended for specific behaviors. 

##### Vk\*.Buffer & .calloc()

Sometimes Vulkan expects a list of objects, either with a single element or multiple.
In these cases, we have to create a Buffer object, which is a subclass of most objects.
Either way, we still have to run a `calloc` command to create the object.
For a singular item, use `calloc(stack)`, and for multiple, use `calloc(x, stack)`, where x is the number of items in the list.

Back to the constructor:

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // Create application information
            ByteBuffer appShortName = stack.UTF8("VulkanBook");
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_3);
        ...
    }
    ...
}
```

In this case we are defining our application information with the structure `VkApplicationInfo`.
As you can see, we cannot create the usual Java objects, but rather we allocate it through the stack and use the following attributes:

- `pApplicationName`: It is basically just some text that will identify the application that uses this instance.
  In this case we use another helper method from the stack class to create a `ByteBuffer` that points to a null-terminated string.
- `applicationVersion`: The version of our application. You can use the method `VK_VERSION_MAJOR/MINOR/PATCH()` methods to create a more detailed version.
- `pEngineName`: The engine name (as a null-terminated string).
- `engineVersion`: The engine version.
- `apiVersion`: The version of the Vulkan API.
  This value should be the highest value of the Vulkan version that his application should use encoded according to what is stated in Vulkan specification
  (major, minor and patch version).
  In this case we are using version `1.3.0`.

Most of the time these attributes are set to `NULL` and `0` respectively.
Since we are allocating the structure in the stack using the `calloc` stack method, all the memory block associated to it will be initialized with zeros,
so we do not need to explicitly set up these common attributes.

## Layers

Vulkan is a layered API.
When you read about the Vulkan core, you can think of that as the mandatory lowest level layer.
On top of that, we there are additional layers that will support useful things like validation and debugging information.
As said before, Vulkan is a low overhead API,
this means that **the driver assumes that you are using the API correctly and does not waste time in performing validations**
(error checking is minimal in the core layer).
If you want the driver to perform extensive validation, you must enable them through specific layers
(validations are handled through extension validation layers).
While we are developing, it is good advice to turn these validation layers on, to check that we are being compliant with the specification.
This can be turned off when our application is ready for delivery.

> [!NOTE]
> to use validation layers, you will need to install [Vulkan SDK](https://www.lunarg.com/vulkan-sdk/) for your platform,
> please consult the specific instructions for your platform. In fact, if you install Vulkan SDK you can use Vulkan Configurator to configure any validation layer
> you want without modifying source code.

> [!WARNING]  
> **macOS** To enable validation layers on macOS, after installing the Vulkan SDK, you will need to configure LWJGL to use the Vulkan Loader.  
> This can be done be setting the following VM paramters: ```-Dorg.lwjgl.librarypath=/usr/local/lib -Dorg.lwjgl.vulkan.libname=libvulkan.1.dylib```  
> Details about MoltenVK and Vulkan Loader on macOS are [here](https://vulkan.lunarg.com/doc/view/1.3.261.1/mac/getting_started.html#moltenvk)

Our `Instance` class constructor receives a boolean parameter indication is validations should be enabled or not.
If validation is requested, we first need to get the ones that are supported by our driver. 

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
                Logger.warn("Request validation but no supported validation layers found. Falling back to no validation");
            }
            Logger.debug("Validation: {}", supportsValidation);
        ...
    }
    ...
}
```

We will get the supported validation layers by invoking the `getSupportedValidationLayers`.
If we have requested validation, but we have not found any layer that can help on this, we log a warning but continue the execution.
Let's move out of the constructor code and check the contents of the `getSupportedValidationLayers`:

```java
public class Instance {
    ...
    private List<String> getSupportedValidationLayers() {
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numLayersArr = stack.callocInt(1);
            vkEnumerateInstanceLayerProperties(numLayersArr, null);
            int numLayers = numLayersArr.get(0);
            Logger.debug("Instance supports [{}] layers", numLayers);
        ...
		}
    }
    ...
}
```

We first need to get the number of supported layers, by invoking the `vkEnumerateInstanceLayerProperties` function. This function receives two parameters:

- The number of layers.
- A pointer to a buffer that will hold all the layers' properties.

This function can be used to get the total number of supported layers, and to get their properties.
We need first to get the number, so the first parameter is an array to get the number of layers, and the second parameter is `null`.
To get the properties, we will need to perform a second call to the same function, once we get the total size.
The next fragment shows how we do retrieve these properties to get their name and store them in a collection.

```java
public class Instance {
    ...
    private List<String> getSupportedValidationLayers() {
    	try (MemoryStack stack = MemoryStack.stackPush()) {
        ...
            var propsBuf = VkLayerProperties.calloc(numLayers, stack);
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
            List<String> supportedLayers = new ArrayList<>();
            for (int i = 0; i < numLayers; i++) {
                VkLayerProperties props = propsBuf.get(i);
                String layerName = props.layerNameString();
                supportedLayers.add(layerName);
                Logger.trace("Supported layer [{}]", layerName);
            }
        ...
		}
    }
    ...
}
```

Once we have the supported layers, we will check if `VK_LAYER_KHRONOS_validation` layer is present. There are many other validation layers,
some of them coming from legacy versions,  but in our case we will use the most standard one.

```java
public class Instance {
    ...
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";
    ...
    private List<String> getSupportedValidationLayers() {
    	try (MemoryStack stack = MemoryStack.stackPush()) {
        ...
            // Main validation layer
            List<String> layersToUse = new ArrayList<>();
            if (supportedLayers.contains(VALIDATION_LAYER)) {
                layersToUse.add(VALIDATION_LAYER);
            }

            return layersToUse;
		}
    }
}
```

In fact, `VK_LAYER_KHRONOS_validation` is a  meta layer.
A meta-layer is basically a collection of layers registered under a single name. Although we are using just a single layer you can easily adapt the method, in case you need add more layers.

Let's get back to the constructor.
Now we have a list of the supported layers' names (array of Strings) we need to transform it to a pointer of null terminated String array:
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
                    Logger.debug("Using validation layer [{}]", validationLayers.get(i));
                    requiredLayers.put(i, stack.ASCII(validationLayers.get(i)));
                }
            }
        ...
    }
    ...
}
```

Now that we've set up all the validation layers, we move on to extensions.
We will first create a method that will retrieve all the supported extensions named `getInstanceExtensions`:
```java
public class Instance {
    ...
    private Set<String> getInstanceExtensions() {
        Set<String> instanceExtensions = new HashSet<>();
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numExtensionsBuf = stack.callocInt(1);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, null);
            int numExtensions = numExtensionsBuf.get(0);
            Logger.trace("Instance supports [{}] extensions", numExtensions);

            var instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, instanceExtensionsProps);
            for (int i = 0; i < numExtensions; i++) {
                VkExtensionProperties props = instanceExtensionsProps.get(i);
                String extensionName = props.extensionNameString();
                instanceExtensions.add(extensionName);
                Logger.trace("Supported instance extension [{}]", extensionName);
            }
        }
        return instanceExtensions;
    }
    ...
}
```
The method, as it will be frequent in many Vulkan API functions,
first invoke the `vkEnumerateInstanceExtensionProperties` to get the number of supported extensions.
After that, creates a method which will hold the extension properties by invoking the same function again,
this time with the number of extensions and the buffer as parameters.

Going back to the `Instance` constructor, if we are using MacOs we need to enable portability extension. In addition to that, because Vulkan is a cross-platform API, links to windowing systems are handled through extensions. In our case, we will be using GLFW, which has extensions for Vulkan, so we need to include this as an extension:
```java
public class Instance {
    ...
    private static final String PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration";
    ...
    public Instance(boolean validate) {
        ...
            Set<String> instanceExtensions = getInstanceExtensions();
            boolean usePortability = instanceExtensions.contains(PORTABILITY_EXTENSION) &&
                    VkUtils.getOS() == VkUtils.OSType.MACOS;

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

We have created a method named `getOS` in `VulkanUtils` class to get the type of OS:

```java
package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK11.VK_SUCCESS;

public class VulkanUtils {

    private VulkanUtils() {
        // Utility class
    }

    public static OSType getOS() {
        OSType result;
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((os.indexOf("mac") >= 0) || (os.indexOf("darwin") >= 0)) {
            result = OSType.MACOS;
        } else if (os.indexOf("win") >= 0) {
            result = OSType.WINDOWS;
        } else if (os.indexOf("nux") >= 0) {
            result = OSType.LINUX;
        } else {
            result = OSType.OTHER;
        }

        return result;
    }
    ...
}
```


Going back to the `Instance` constructor, depending on if we have enabled validation or not, we will need to add the extension used for debugging.
We will use the `VK_EXT_debug_utils` extension
(which should be used instead of an older debug extension such as `VK_EXT_debug_report` and `VK_EXT_debug_marker`).
In addition to that, if we are on macOS and the `VK_KHR_portability_enumeration` is available we need to use that extension.

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            var additionalExtensions = new ArrayList<ByteBuffer>();
            if (supportsValidation) {
                additionalExtensions.add(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            if (usePortability) {
                additionalExtensions.add(stack.UTF8(PORTABILITY_EXTENSION));
            }
            int numAdditionalExtensions = additionalExtensions.size();
            PointerBuffer requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + numAdditionalExtensions);
            requiredExtensions.put(glfwExtensions);
            for (int i = 0; i < numAdditionalExtensions; i++) {
                requiredExtensions.put(additionalExtensions.get(i));
            }
            requiredExtensions.flip();
        ...
    }
    ...
}
```

Additionally, if we have enabled the debug extension, we will be interested in setting a callback, so we can, for example, log the information reported.
We have already enabled the debugging extension, but we need to create it.
We also need to pass this extension while creating the instance to properly log errors while creating and destroying the instance:
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
    private static final String DBG_CALL_BACK_PREF = "VkDebugUtilsCallback, {}";
    ...
    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack() {
        return VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                        Logger.info(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        Logger.warn(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        Logger.error(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else {
                        Logger.debug(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    }
                    return VK_FALSE;
                });
    }
    ...
}
```
In this method, we instantiate a `VkDebugUtilsMessengerCreateInfoEXT` which is defined by the following attributes:
- `messageSeverity`: This will hold a bitmask with the levels of the messages that we are interested in receiving. In our case, we will receive error and warning messages.
- `messageType`: This will hold a bitmask with the types of messages that we are interested in receiving. In our case, we will receive validation and performance messages.
- `pfnUserCallback`:  The function that will be invoked when a message matches the criteria established by the `messageSeverity` and `messageType` fields.
  In our case, we just log the message with the proper logging level according to the `messageSeverity` parameter.

Finally, going back to the constructor, we have everything we need to create the Vulkan instance.
To do so, we need to set up yet another structure: `VkInstanceCreateInfo`, which is defined as follows:

- Next extension: In our case, the debug extension configuration structure or `NULL` if no validation is requested or supported.
- The application information structure that we created at the beginning of the chapter.
- The enabled layers.
- The extensions requested.

With that structure, we invoke the `vkCreateInstance` function, and we will get a pointer to the Vulkan instance.
We store that address as a long attribute named `vkInstance`.
If we have enabled the portability extension, we need to set the `VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR` fla.

```java
public class Instance {
    ...
    public Instance(boolean validate) {
        ...
            // Create instance info
            var instanceInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(extension)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(requiredLayers)
                    .ppEnabledExtensionNames(requiredExtensions);
            if (usePortability) {
                instanceInfo.flags(0x00000001); // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
            vkInstance = new VkInstance(pInstance.get(0), instanceInfo);
            ...
        }
    }
    ...
}
```

The last step is to instantiate the debug extension.
We have passed its configuration while creating the Vulkan instance, but this will only be used in the instance creation and destruction phases.
For the rest of the code, we will need to instantiate by calling the `vkCreateDebugUtilsMessengerEXT` Vulkan function.
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

Most of the Vulkan functions return an `int` value that is used to check if the call as succeeded or not.
To check this, a utility method has been defined in the `VulkanUtils` class that throws a `RuntimeException` if the call does not return `VK_SUCCESS`.
It also maps some error codes to string which help identifying the cause.

```java
public class VulkanUtils {
    ...
    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            String errCode = switch (err) {
                case VK_NOT_READY -> "VK_NOT_READY";
                case VK_TIMEOUT -> "VK_TIMEOUT";
                case VK_EVENT_SET -> "VK_EVENT_SET";
                case VK_EVENT_RESET -> "VK_EVENT_RESET";
                case VK_INCOMPLETE -> "VK_INCOMPLETE";
                case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
                case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
                case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
                case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
                case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
                case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
                case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
                default -> "Not mapped";
            };
            throw new RuntimeException(errMsg + ": " + errCode + " [" + err + "]");
        }
    }
}
```

The `Instance` class provides two additional methods, one for free resources (named `cleanup`) and another one to get the address of the instance
(named `getVkInstance`).

```java
public class Instance {
    ...
    public void cleanup() {
        Logger.debug("Destroying Vulkan instance");
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null);
        }
        vkDestroyInstance(vkInstance, null);
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free();
            debugUtils.free();
        }
    }

    public VkInstance getVkInstance() {
        return vkInstance;
    }
}
```

We will crate a new class, named `VkCtx` which will group most relevant Vulkan context classes together. By now, it will only have a reference to the `Instance` class:
```java
package org.vulkanb.eng.graph.vk;

import org.vulkanb.eng.EngCfg;

public class VkCtx {

    private final Instance instance;

    public VkCtx() {
        var engCfg = EngCfg.getInstance();
        instance = new Instance(engCfg.isVkValidate());
    }

    public void cleanup() {
        instance.cleanup();
    }
}
```

Finally, we can will use the Instance `VkCtx` class in our `Render` class, in the constructor and `cleanup` methods.
```java
public class Render {
    private final VkCtx vkCtx;

    public Render(EngCtx engCtx) {
        vkCtx = new VkCtx();
    }

    public void cleanup() {
        vkCtx.cleanup();
    }
    ...
}
```

We have added a new configuration variable to control if validation should be used or not:

```java
public class EngCfg {
...
    private boolean vkValidate;

    private EngineProperties() {
        ...
            vkValidate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
        ...
    }
    ...
    public boolean isVkValidate() {
        return vkValidate;
    }
}
```

Of course, you just need to add a new variable in the `eng.properties` file (located under `src/resources` folder):
```
...
vkValidate=true
```

And that's all! As you can see, we have to write lots of code just to set up the Vulkan instance.You can see now why Vulkan is considered an explicit API. A whole chapter passed, and we can't even clear the screen. So, contain your expectations, since in the next chapters we will continue writing lots of code required to set up everything.
It will take some time to draw something, so please be patient. The good news is that when everything is set up, adding incremental features to draw more complex models or to support advanced techniques should require less amount of code. And if we do it correctly, we get a good understanding of Vulkan.

[Next chapter](../chapter-03/chapter-03.md)
