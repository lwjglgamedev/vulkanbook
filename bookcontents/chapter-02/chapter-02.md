# Vulkan instance

In this chapter we will be having our first taste of Vulkan, we will start by creating a Vulkan instance. This is the very first thing that will be created when dealing with Vulkan. Basically a Vulkan instance is where all the application state is glued together. In Vulkan there is no global state, all that information is organized around a Vulkan instance.

You can find the complete source code for this chapter [here](../../booksamples/chapter-02).

## Instance first steps

Usually you will have a single Vulkan instance for each application, but the spec allows you to have more. A potential use case for having more than one is if you are using a legacy library that already uses Vulkan (maybe even different version) and do you not want that to interfere with your code. You could then setup a separate instance just for your code. We will start from scratch in this book, therefore, we will use just a single instance.

Mos of the Vulkan related code will be placed under the package `org.vulkanb.eng.graph.vk` package. In this case, we will create a new class named `Instance` to wrap all the initialization code. So let's start by coding the constructor, which starts like this:

```java
    public Instance(boolean validate) {
        LOGGER.debug("Creating Vulkan instance");
        try (MemoryStack stack = MemoryStack.stackPush()) {
```

Before going on, for those who are not familiar with [LWJGL](https://www.lwjgl.org/), we will explain the purpose of the `MemoryStack`. In order to share data with native code, LWJGL uses direct (off-heap) buffers instead of relaying on JNI (which would be quite slow). You can think on direct buffers as C pointers to a region of memory than can be accessed both by Java and native code. But Java has a major drawback, we cannot allocate objects in the stack, such as in C, even for these direct buffers. This is specially painful for short lived buffers that may be used to pass some initialization info to native methods. To solve this, LWJGL comes with the `MemoryStack`, which is basically a direct allocated stack, where we can reserve direct memory when we need to exchange short lived info with native functions. You can read about all the glory details [here](https://github.com/LWJGL/lwjgl3-wiki/wiki/1.3.-Memory-FAQ). Although is not perfect, it really helps this information sharing, and what is more important, everything allocated in the stack inside a try / catch block is automatically freed when we exit that block. We will see if [Project Panama](https://openjdk.java.net/projects/panama/) can finally ease the invocation of native code from Java.

So let's back to the constructor:

```java
// Create application information
ByteBuffer appShortName = stack.UTF8("VulkanBook");
VkApplicationInfo appInfo = VkApplicationInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
        .pApplicationName(appShortName)
        .applicationVersion(1)
        .pEngineName(appShortName)
        .engineVersion(0)
        .apiVersion(VK_MAKE_VERSION(1, 2, 0));
```

Here you can see the basis of all the Vulkan calls. Almost every Vulkan function will use a structure, and almost all of them a type attribute, which, as its names suggests, defines its type, that is, what information it models. In this case we are defining our application information with the structure `VkApplicationInfo`. As you can see, we allocate it through the stack and use the following attributes:

- `sType`: Structure type. In this case: `VK_STRUCTURE_TYPE_APPLICATION_INFO`.
- `pApplicationName`: It is basically just some text that will identify the application that uses this instance. In this case we use another helper method from the stack class to create a `ByteBuffer` that points to a null-terminated string.
- `applicationVersion`: The version of our application.
- `pEngineName`: The engine name.
- `engineVersion`: The engine version.
- `apiVersion`: The version of the Vulkan API. This value should be the highest value of the Vulkan version that his application should use encoded according to what is stated in Vulkan specification (major, minor and patch version). In this case we use version `1.2.0`.

If you see other Vulkan samples you may see that almost every structure has other two additional attributes that we have not used in this case, which are:

- `pNext`: Which is a pointer to a extension-specific structure. If no extensions are used for a specific structure `NULL` can be passed.
- `flags`: which are used to control specific behaviors. (In the `VkApplicationInfo` case the structure even does not have this attribute).

Most of the time these attributes are set to `NULL` and `0` respectively. Since we are allocating the structure in the stack using the `callocStack` stack method, all the memory block associated to it will be initialized with zeros so we do not need to explicitly set up these common attributes. 

## Layers

Vulkan is a layered API. When you read about Vulkan core, you can think as the mandatory lowest level layer. On top of that, we there are additional layers that will support validation and debugging. As said before, Vulkan is a low overhead API, this means that the driver assumes that you are using the API correctly and does not waste time in performing validations (error checking is minimal in the core layer). If you want the driver to perform extensive validation you must enable them through specific layers (validations are handled through extension validation layers). While we are developing it is good advice to turn these validation layers on, to check that we are being compliant with the specification. This can be turned off when our application is ready for delivery.

Our `Instance` class constructor receives a boolean parameter indication is validations should be enabled or not. If validation is requested we need first to get the ones that are supported by our driver. 

```java
// Validation layers
String[] validationLayers = validate ? getSupportedValidationLayers(stack) : null;
int numValidationLayers = validationLayers != null ? validationLayers.length : 0;
boolean supportsValidation = validate;
if (validate && numValidationLayers == 0) {
    supportsValidation = false;
    LOGGER.warn("Request validation but no supported validation layers found. Falling back to no validation");
}
LOGGER.debug("Validation: {}", supportsValidation);
```

We will get the supported validation layers by invoking the `getSupportedValidationLayers`. If we have requested validation, but we have not found any layer that can help on this, we log a warning but continue the execution. Let's move out of the constructor code and check the contents of the `getSupportedValidationLayers`:

```java
private String[] getSupportedValidationLayers(MemoryStack stack) {
   Set<String> supportedLayers = new HashSet<>();
    int[] numLayersArr = new int[1];
    vkEnumerateInstanceLayerProperties(numLayersArr, null);
    int numLayers = numLayersArr[0];
    LOGGER.debug("Instance supports [{}] layers", numLayers);
```

We first need to get the number of supported layers, by invoking the `vkEnumerateInstanceLayerProperties` function. This function can received two parameters:

- The number of layers.
- A pointer to a buffer that will hold all the layers properties.

This function can be used to get the total number of supported layers, and also to get the properties of them. We need first to get the number, so the first parameter is an array to get the number of layers, and the second parameter is `null`. In order to get the properties we will need to perform a second call to the same function, once we get the total size. The next fragment shows how we do retrieve these properties to get their name and store them in a `Set`.

```java
    VkLayerProperties.Buffer propsBuf = VkLayerProperties.callocStack(numLayers, stack);
    vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
    for (int i = 0; i < numLayers; i++) {
        VkLayerProperties props = propsBuf.get(i);
        String layerName = props.layerNameString();
        supportedLayers.add(layerName);
        LOGGER.debug("Supported layer [{}]", layerName);
    }
```

Once we have the supported layers, we need to select which ones do we want to activate. In order to do so, we construct a list of the preferred layers like this:

```java
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
```

We first try to aim for the `VK_LAYER_KHRONOS_validation` meta layer. A meta layer is basically a collection of layers registered under a single name. Then we go down in our priority list combining other meta layers or selecting a list of single layers. After that, we basically check the presence of these preferred layers (ordered by priority) to get the ones that are supported (Once we match with a preferred meta-layer or list of layers, we just pick that):

```java
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
```

Let's get back to the constructor. Now we have a list of the names of the supported layers (array of Strings) we need to transform it to a pointer of a list of null terminated Strings:

```java
// Set required  layers
PointerBuffer requiredLayers = null;
if (supportsValidation) {
    requiredLayers = stack.mallocPointer(numValidationLayers);
    for (int i = 0; i < numValidationLayers; i++) {
        LOGGER.debug("Using validation layer [{}]", validationLayers[i]);
        requiredLayers.put(i, stack.ASCII(validationLayers[i]));
    }
}
```

Now that we have setup all the validation layers is time for extensions. As it has been said before, Vulkan is  a cross platform API, hence, the link between Vulkan an any windowing system is handled through extensions. In our case we will be using GLFW, so we need to include this as an extension with the following code:

```java
// GLFW Extension
PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
if (glfwExtensions == null) {
    throw new RuntimeException("Failed to find the GLFW platform surface extensions");
}
```

Depending if we have enabled validation or not we will need to add another extension to support debugging:

```java
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
```

Additionally, if we have enabled the debug extension, we will be interested in setting a callback so we can, for example, log the information reported. We have already enabled the debug extension, but we need to configure it. This is done through an extension structure used while creating the Vulkan instance. As mentioned at the beginning, most of Vulkan creation structures, reserve an extension parameter which can be used to configure extension-specif data. Therefore, if validation is enabled, we will execute the following code:

```java
long extension = MemoryUtil.NULL;
if (supportsValidation) {
    VkDebugReportCallbackCreateInfoEXT dbgCreateInfo = VkDebugReportCallbackCreateInfoEXT.callocStack(stack)
        .sType(EXTDebugReport.VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT)
        .flags(EXTDebugReport.VK_DEBUG_REPORT_ERROR_BIT_EXT | EXTDebugReport.VK_DEBUG_REPORT_WARNING_BIT_EXT)
        .pfnCallback(DBG_FUNC)
        .pUserData(MemoryUtil.NULL);
    extension = dbgCreateInfo.address();
}
```

We create an instance of the class `VkDebugReportCallbackCreateInfoEXT`, in which, through the flags attribute we restrict the callbacks to error or warning messages. The callback is set using the `pfnCallback` method. In this case, we have created a function which has been defined like this:

```java
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
```

This method just logs the message reported using the specified severity level. Finally, we have everything we need in order to create the Vulkan instance. In order to do so, we need to setup another structure, the `VkInstanceCreateInfo` one, which is defined as follows:

- Structure type: In this case: `VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO`.
- Next extension: In our case, the debug extension configuration structure or `NULL` if no validation is requested or supported.
- The application information structure that we created at the beginning of the chapter.
- The enabled layers.
- The extensions requested.

With that structure, we invoke the `vkCreateInstance` function, and we will get a pointer to the Vulkan instance. We store that address as a long attribute named `vkInstance`.

```java
// Create instance info
VkInstanceCreateInfo instanceInfo = VkInstanceCreateInfo.callocStack(stack)
    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
    .pNext(extension)
    .pApplicationInfo(appInfo)
    .ppEnabledLayerNames(requiredLayers)
    .ppEnabledExtensionNames(requiredExtensions);

PointerBuffer pInstance = stack.mallocPointer(1);
vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
this.vkInstance = new VkInstance(pInstance.get(0), instanceInfo);
```

Most of the Vulkan functions return an `int` value that is used to check if the call as succeeded or not. To check this, an utility method has been defined in the `VulkanUtils` class that throws a `RuntimeException` if the call does not return `VK_SUCCESS`.

```java
package org.vulkanb.eng.graph.vk;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

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

The `Instance` class provides two additional methods, one for free resources (named `cleanUp`) and another one to get the address of the instance (named `getVkInstance`).

```java
public void cleanUp() {
    LOGGER.debug("Destroying Vulkan instance");
    vkDestroyInstance(this.vkInstance, null);
}
// ...
public VkInstance getVkInstance() {
    return vkInstance;
}
```

Finally, we an use the Instance class in our `Render` class, in the `init` and `cleanUp` methods.

```java
public void cleanUp() {
    this.instance.cleanUp();
}

public void init(Window window) {
    EngineProperties engProps = EngineProperties.getInstance();
    this.instance = new Instance(engProps .isValidate());
}
```

We have added a new configuration variable to control if validation should be used or not:

```java
private EngineProperties() {
...
    private boolean validate;

    private EngineProperties() {
        ...
            this.validate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
        ...
    }
    ...
    public boolean isValidate() {
        return this.validate;
    }
```

And that's all! As you can see we have to write lots of code just to setup the Vulkan instance. You can see now why Vulkan is considered an explicit API. A whole chapter and we don't even have been able to clear the screen. So, contain your expectations, since in the next chapters we will continue writing lots of code required to setup everything. It will take some time in order to draw something. The good news is that when everything is set up, adding incremental features to draw more complex models or to support advanced techniques should require less amount of code.

[Next chapter](../chapter-03/chapter-03.md)