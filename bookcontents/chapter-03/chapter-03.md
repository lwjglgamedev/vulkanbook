# Physical device

In this chapter we will progress in the definition of the Vulkan structures required to render a 3D scene. Specifically, we will setup the Physical device and a Surface.

You can find the complete source code for this chapter [here](../../booksamples/chapter-03).

## Physical device selection

A physical device represents any piece of hardware that provides a complete implementation of the Vulkan interface (usually a physical GPU). You can have several Vulkan capable physical devices (you may have more than one GPU), but you will usually just use one (we will not be dealing with multi-GPU rendering here). A side note, as we progress through this book, we will define many concepts. In order to help you in understanding the  relationship between all of them, we will be filling up a class diagram. Here you can find the ones that shows up the elements described so far.

![UML Diagram](yuml-01.svg)

So let's go back to coding and start by encapsulating all the code for selecting and creating a physical device in a new class named `Physdevice` (in the package `org.vulkanb.eng.graph.vk`). As it has been said before, we may have more than one Vulkan physical devices in our host machine. In order to get the most appropriate one, this class provides a `static` method to do that selection and construct the associated object for us. This method method, named `createPhysicalDevice`, iterates over all the available devices and picks the most suitable one. The method starts like this:

```java
public class PhysicalDevice {
    ...
    public static PhysicalDevice createPhysicalDevice(Instance instance, String prefferredDeviceName) {
        LOGGER.debug("Selecting physical devices");
        PhysicalDevice selectedPhysicalDevice = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Get available devices
            PointerBuffer pPhysicalDevices = getPhysicalDevices(instance, stack);
            int numDevices = pPhysicalDevices != null ? pPhysicalDevices.capacity() : 0;
            if (numDevices <= 0) {
                throw new RuntimeException("No physical devices found");
            }
        ...
    }
    ...
}
```

We start by getting a pointer with the list of available physical devices, by calling the method `getPhysicalDevices`. Once we get that list, if there are no suitable devices we just throw a `RuntimeException` (no sense in going on). That list contains the handles that will allow us to create Vulkan physical devices. After that, we start a loop to iterate over that list and create a `VkPhysicalDevice` instance for each of those handles. The `VkPhysicalDevice` class models an opaque handle to the physical device. This is the code for that loop:

```java
public class PhysicalDevice {
    ...
    public static PhysicalDevice createPhysicalDevice(Instance instance, String prefferredDeviceName) {
        ...
            // Populate available devices
            List<PhysicalDevice> devices = new ArrayList<>();
            for (int i = 0; i < numDevices; i++) {
                VkPhysicalDevice vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.getVkInstance());
                PhysicalDevice physicalDevice = new PhysicalDevice(vkPhysicalDevice);

                String deviceName = physicalDevice.getDeviceName();
                if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                    LOGGER.debug("Device [{}] supports required extensions", deviceName);
                    if (prefferredDeviceName != null && prefferredDeviceName.equals(deviceName)) {
                        selectedPhysicalDevice = physicalDevice;
                        break;
                    }
                    devices.add(physicalDevice);
                } else {
                    LOGGER.debug("Device [{}] does not support required extensions", deviceName);
                    physicalDevice.cleanup();
                }
            }
        ...
    }
    ...
}
```

Each `PhysicalDevice` instance will retrieve all the properties and features we need to use it and to support the device selection. We need to check for two things:

- That the device supports the graphics queue family. This is done by calling the `hasGraphicsQueueFamily` method.
- That the device is capable of presenting images to a screen. This is done by calling the `hasKHRSwapChainExtension` method.

You may be surprised that this is not part of the core API, but think that you may have GPUs that may be used just for computing or that are not even attached to any display (they may do off-screen rendering). So being this capability optional, we need to be sure that the selected device supports it. We will show later on the implementation of those two methods.

If the device fulfills both conditions, we then check if its name matches the preferred device name (if this has been specified). If so, we already have our candidate and there's no need to continue so we break the loop. If not, we just add that device to the list of potential candidates and continue with the loop. If the device does not fulfill the conditions we just simply clean the resources allocated for them and also continue with the loop.

Once we have finished with the loop, if we have not selected a device yet we just pick the first one from the list. You can add more sophistication to this selection process trying to pick the most capable one, but at this moment this approach should be enough. If no device has been selected we throw another `RuntimeException`.

```java
public class PhysicalDevice {
    ...
    public static PhysicalDevice createPhysicalDevice(Instance instance, String prefferredDeviceName) {
        ...
            // No preferred device or it does not meet requirements, just pick the first one
            selectedPhysicalDevice = selectedPhysicalDevice == null && !devices.isEmpty() ? devices.remove(0) : selectedPhysicalDevice;

            // Clean up non-selected devices
            for (PhysicalDevice physicalDevice : devices) {
                physicalDevice.cleanup();
            }

            if (selectedPhysicalDevice == null) {
                throw new RuntimeException("No suitable physical devices found");
            }
            LOGGER.debug("Selected device: [{}]", selectedPhysicalDevice.getDeviceName());
        }

        return selectedPhysicalDevice;
    }
    ...
}
```

Here's the definition of the `getPhysicalDevices` method used in the code listed above.

```java
public class PhysicalDevice {
    ...
    protected static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        PointerBuffer pPhysicalDevices;
        // Get number of physical devices
        IntBuffer intBuffer = stack.mallocInt(1);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, null),
                "Failed to get number of physical devices");
        int numDevices = intBuffer.get(0);
        LOGGER.debug("Detected {} physical device(s)", numDevices);

        // Populate physical devices list pointer
        pPhysicalDevices = stack.mallocPointer(numDevices);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }
    ...
}
```

It is just another static method that enumerates the available physical devices by calling the `vkEnumeratePhysicalDevices` function. This functions receives the Vulkan instance, and an `IntBuffer` where the number of devices will be returned. As in some other cases, we use that call twice the first one to return the number of devices, and the second one, after allocating a `PointerBuffer` for the results, to get a list of pointers to access the data of each of them.

Let's now review the definition of the instance attributes and methods of the `PhysicalDevice` class itself. Let's start with attributes.

```java
public class PhysicalDevice {

    private static final Logger LOGGER = LogManager.getLogger();
    private VkExtensionProperties.Buffer vkDeviceExtensions;
    private VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    private VkPhysicalDevice vkPhysicalDevice;
    private VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    private VkPhysicalDeviceProperties vkPhysicalDeviceProperties;
    private VkQueueFamilyProperties.Buffer vkQueueFamilyProps;
    ...
}
```

Let's explain the different attributes:

- `vkPhysicalDeviceProperties`: It contains the physical device properties, such as the device name, the vendor, its limits, etc.
- `vkDeviceExtensions`:  It is a `Buffer` containing a list of supported extensions (name and version).
- `vkQueueFamilyProps`: It is also a `Buffer` which will hold the queue families supported by the device (More on this later on)
- `vkPhysicalDeviceFeatures`: It contains fine grained features supported by this device, such as if it supports depth clamping, certain types of shaders, etc.
- `vkMemoryProperties`: It contains information related to the different memory heaps the this device supports.

The constructor basically populates these structures, with the exception of the `VkPhysicalDevice` which is passed as a parameter:

```java
public class PhysicalDevice {
    ...
    private PhysicalDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            IntBuffer intBuffer = stack.mallocInt(1);

            // Get device properties
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(this.vkPhysicalDevice, vkPhysicalDeviceProperties);

            // Get device extensions
            vkCheck(vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (String) null, intBuffer, null),
                    "Failed to get number of device extension properties");
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0));
            vkCheck(vkEnumerateDeviceExtensionProperties(this.vkPhysicalDevice, (String) null, intBuffer, vkDeviceExtensions),
                    "Failed to get extension properties");

            // Get Queue family properties
            vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, intBuffer, null);
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(this.vkPhysicalDevice, intBuffer, vkQueueFamilyProps);

            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(this.vkPhysicalDevice, vkPhysicalDeviceFeatures);

            // Get Memory information and properties
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(this.vkPhysicalDevice, vkMemoryProperties);
        }
    }
    ...
}
```

The class provides a `cleanup` method, to free its resources:

```java
public class PhysicalDevice {
    ...
    public void cleanup() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Destroying physical device [{}]", vkPhysicalDeviceProperties.deviceNameString());
        }
        vkMemoryProperties.free();
        vkPhysicalDeviceFeatures.free();
        vkQueueFamilyProps.free();
        vkDeviceExtensions.free();
        vkPhysicalDeviceProperties.free();
    }
    ...
}
```

Additionally, it also provides the *getters* for the properties described above:

```java
public class PhysicalDevice {
    ...
    public String getDeviceName() {
        return vkPhysicalDeviceProperties.deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return vkMemoryProperties;
    }

    public VkPhysicalDevice getVkPhysicalDevice() {
        return vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties getVkPhysicalDeviceProperties() {
        return vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return vkQueueFamilyProps;
    }
    ...
}
```

Now we can check the implementation of the method that checks if the device supports the KHR Swapchain extension. That is, the method that checks if this device is capable of rendering images to the screen. This method, named `vkDeviceExtensions` basically iterates over the supported extensions checking if there's one named `KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME`:

```java
public class PhysicalDevice {
    ...
    private boolean hasKHRSwapChainExtension() {
        boolean result = false;
        int numExtensions = vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = vkDeviceExtensions.get(i).extensionNameString();
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensionName)) {
                result = true;
                break;
            }
        }
        return result;
    }
}
```

## Queue families

Since the only pending method to present now is the one named `hasGraphicsQueueFamily` it is now the moment to talk a little bit about Vulkan queues. In Vulkan, any work is performed by submitting commands buffers through specific queues. We do not command the GPU to immediately draw a specific shape, we submit a command to a queue which contains the instructions to render that shape. Commands in those queues are consumed and executed asynchronously. Devices have different types of queues, which are organized in families. Each queue family only accepts a specif set of command types. For example, we may have graphic commands used to render and compute commands, each of these command types may require to be submitted to different types of queue. In our case, we want to be sure that the selected device is capable of handling graphics commands, which is what we check within the `hasGraphicsQueueFamily` method: 

```java
public class PhysicalDevice {
    ...
    private boolean hasGraphicsQueueFamily() {
        boolean result = false;
        int numQueueFamilies = vkQueueFamilyProps != null ? vkQueueFamilyProps.capacity() : 0;
        for (int i = 0; i < numQueueFamilies; i++) {
            VkQueueFamilyProperties familyProps = vkQueueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }
    ...
}
```

We iterate over the supported queue families, in order to check if they have a flag that shows that this queue family is capable of supporting graphics commands. We will explain in more detail the concepts related to queues and commands as we progress through the book.

## Logical Device

Now that we have a physical device we can start with the logical device. Vulkan separates these two concepts, while a physical device directly maps directly to a physical capable hardware, a logical device represents the actual interface to that hardware. The logical device will store all the resources that we create alongside with their state.

In case you wonder, you may create more than one logical device, it is another layer of abstraction over our GPU (the physical device) that allows us to manage the resources. In any case,  here we will stick just with one instance.  The next picture shows the class diagram updated.

![UML Diagram](yuml-02.svg)

As in our previous samples, we will create a new class, named `Device` to wrap device creation and some utility methods around it. So let's update our class diagram. The `Device` class starts like this:

```java
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
    private PhysicalDevice physicalDevice;
    private VkDevice vkDevice;
```

The Vulkan structure `VkDevice` is the one that will hold or Vulkan logical device. We will use that structure for the creation of the resources we will need later on. We will hold also a reference to the `PhysicalDevice` instance (which will be passed in the constructor) for convenience (some calls will require later on both the logical and the physical devices). It is turn now for the constructor, which starts like this:

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        LOGGER.debug("Creating device");

        this.physicalDevice = physicalDevice;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ...
        }
    }
    ...
}
```

As anticipated before, we first store a reference to the physical device and start the familiar try/catch block to allocate short-lived objects in the LWJGL stack. The next thing we will do is define the extensions that our device is going to use.

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        ...
            // Define required extensions
            PointerBuffer requiredExtensions = stack.mallocPointer(1);
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));
        ...
    }
    ...
}
```

If you recall, when selecting the physical device we checked if it supported the KHR Swap chain extension, now it is the turn to explicitly say that we are going to use it. In order to define that we create a `PointerBuffer` which will hold a list of `null` terminated strings.

After that, we need set the features that we want to use. Features are certain capabilities which can be present or not in your physical device. For the ones that are present we can choose which ones to enable for our logical device. Some features control if compressed textures are enabled or not, if 64 bit floats are supported, etc. We could just simple use the set of features already supported by our physical device but doing this we may affect performance. By now we will not be enabling any feature, so we just allocate an empty structure.

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        ...
            // Set up required features
            VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.callocStack(stack);
        ...
    }
    ...
}
```

Then we need to enable the queues families that this logical device will use. Later on, when we create queues, we will need to specify the queue family which it belongs to. If that queue family has been not be enabled for the logical device we will get an error.  In this case we will opt for enabling all the supported queues families (which is an structure that we obtained while creating the physical device).

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        ...
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
        ...
    }
    ...
}
```

We basically create a `Buffer` of `VkDeviceQueueCreateInfo` structures which will hold the index of each queue family and its priority. The priority is mechanism that allows us to instruct the driver to prioritize the work submitted by using the priorities assigned to each queue family. However, this is prioritization mechanism is not mandated in the specification. Drivers are free to apply the algorithms they consider in order to balance the work. Therefore, in our case we will just set priorities to a fixed value of `0.0` (which is the default value  for the lowest priority, we simply don't care).

However, if you examine the code, for the priorities attribute, we are using a `FloatBuffer`. Why is this? Why we just don't pass a single value? The reason is that when we create a logical device, all the queues that can be used are also pre-created. When later on we will view how to create a queue, you must keep in mind that we are not instantiating it, it is already created, we are just retrieving a handle to it. But, how this is related to the fact that we are using a `FloatBuffer` for priorities? Vulkan specification sets an attribute to set the numbers of queues created which is named `queueCount`. For each of those queues, we need to set up a priority for each of them , this is why we use a `FloatBuffer` for them (which is the equivalent of a pointer to an array of `float`'s) . Therefore, since the length of the priorities should be equal to the number of queues. LWJGL as opted to remove that attribute (`queueCount`) because the total number of queues can be derived from the other data. In our example, we have decided to create as many queues as the queue family supports, which is obtained from the attribute `queueCount` from the `VkQueueFamilyProperties` structure.

With all of the above we can fill up the structure required to create a logical device, which is called `VkDeviceCreateInfo`:

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        ...
            VkDeviceCreateInfo deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .ppEnabledExtensionNames(requiredExtensions)
                    .pEnabledFeatures(features)
                    .pQueueCreateInfos(queueCreationInfoBuf);
        ...
    }
    ...
}
```

Now we are ready to create the logical device by using the `vkCreateDevice` function which will receive the structure we have just created and a pointer to get a handle as the result. Finally, we use all that data to create an instance of the class `VkDevice`:

```java
public class Device {
    ...
    public Device(PhysicalDevice physicalDevice) {
        ...
            PointerBuffer pp = stack.mallocPointer(1);
            vkCheck(vkCreateDevice(physicalDevice.getVkPhysicalDevice(), deviceCreateInfo, null, pp),
                    "Failed to create device");
            vkDevice = new VkDevice(pp.get(0), physicalDevice.getVkPhysicalDevice(), deviceCreateInfo);
        }
    }
    ...
}
```

To complete the `Device` class, here are the rest of the methods:

```java
public class Device {
    ...
    public void cleanup() {
        LOGGER.debug("Destroying Vulkan device");
        vkDestroyDevice(vkDevice, null);
    }

    public PhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }

    public VkDevice getVkDevice() {
        return vkDevice;
    }

    public void waitIdle() {
        vkDeviceWaitIdle(vkDevice);
    }
}
```

As you can see they are basically some *getters* and a `cleanup` method to free resources plus one additional method name `waitIdle` which will be used later on. This method just calls the Vulkan `vkDeviceWaitIdle` function which waits that all the pending operations on any queue for that device complete.

## Surface

Once that we have defined the `PhysicalDevice` it is time to create a surface to display the rendering results. We have already developed the code for creating a window, but still we need to link it somehow with Vulkan. This is handled through the WSI (Window System Integration) extensions. Remember that Vulkan is a cross-platform API which can be used in different windowing Systems, so in order to link our windows and Vulkan, we need to enable that capability through an extension (no part of the core API). If you recall, in the `Instance` class we already enabled the extensions required to integrate with the GLFW library (by calling the `GLFWVulkan.glfwGetRequiredInstanceExtensions()` method). In my case, the required extensions had this names:

- `VK_KHR_surface`

- `VK_KHR_win32_surface`

In order to create the surface we will create a new class named `Surface`. The following picture updates all the concepts viewed up to now with this new class.

![UML Diagram](yuml-03.svg)

The source code of the `Surface` class is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;

import java.nio.LongBuffer;

public class Surface {

    private static final Logger LOGGER = LogManager.getLogger();
    private PhysicalDevice physicalDevice;
    private long vkSurface;

    public Surface(PhysicalDevice physicalDevice, long windowHandle) {
        LOGGER.debug("Creating Vulkan surface");
        this.physicalDevice = physicalDevice;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(this.physicalDevice.getVkPhysicalDevice().getInstance(), windowHandle,
                    null, pSurface);
            vkSurface = pSurface.get(0);
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying Vulkan surface");
        KHRSurface.vkDestroySurfaceKHR(physicalDevice.getVkPhysicalDevice().getInstance(), vkSurface, null);
    }

    public long getVkSurface() {
        return vkSurface;
    }
}
```

As you can see we just use the method `glfwCreateWindowSurface` from the `GLFWVulkan` class to create the surface. The handle obtained in this method will be used later on to be able to construct the artifacts required to render something in the screen. We also retrieve the surface capabilities by calling the `vkGetPhysicalDeviceSurfaceCapabilitiesKHR` function. The `Surface`class also provide a `cleanup` methods to free the allocated resources after its usage.

## Queues

As it was introduced before, the way to submit work to our GPU is by submitting command buffers to queues. These command buffers contain the instructions that will be executed when that job is executed. An important concept to stress out when examining the instructions for commands, is that this will not be executed immediately, we are just recording the commands. It is critical to keep this in mind when dealing with resources, we cannot record a command and modify those resources while the command is queued or even being executed.

![UML Diagram](yuml-04.svg)

Again, we will create a new class which models queue retrieval, named `Queue`. The `Queue` class itself is also very simple:

```java
package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.VK11.*;

public class Queue {

    private static final Logger LOGGER = LogManager.getLogger();

    private VkQueue vkQueue;

    public Queue(Device device, int queueFamilyIndex, int queueIndex) {
        LOGGER.debug("Creating queue");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device.getVkDevice(), queueFamilyIndex, queueIndex, pQueue);
            long queue = pQueue.get(0);
            vkQueue = new VkQueue(queue, device.getVkDevice());
        }
    }

    public VkQueue getVkQueue() {
        return vkQueue;
    }

    public void waitIdle() {
        vkQueueWaitIdle(vkQueue);
    }
    ...
}
```

In the `Queue` constructor we just invoke the `vkGetDeviceQueue` function which receives the following parameters:

- The logical device reference.
- The index of the queue family that this queue belongs to. If you remember, when we created the device, we specified the queue families allowed, this index should match one of the indices assigned to those queue families.
- The index of this queue within the queue family itself. When we created the logical device define the queues that were being pre-created. With this parameter which one of those queues we want to get its handle.

After calling this method we will get a handle to our queue. The rest of the code consist on  a *getter* to get that handle, another *getter* for the queue family index and another useful method to wait for the queue to complete all its pending jobs. You may have noticed that the `Queue` class does not provide a `cleanup` method. This again due to the fact, that queues were pre-created when the logical device was instantiated, so there is no need to remove them. When the logical device is cleaned up, their queues will also be destroyed.

In the rest of the code we define an inner class named `GraphicsQueue` which we will use to create queues for submitting render tasks. This class extends the `Queue` class and provides a helper method to select the most appropriate queue family:

```java
public class Queue {
    ...
    public static class GraphicsQueue extends Queue {

        public GraphicsQueue(Device device, int queueIndex) {
            super(device, getGraphicsQueueFamilyIndex(device), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(Device device) {
            int index = -1;
            PhysicalDevice physicalDevice = device.getPhysicalDevice();
            VkQueueFamilyProperties.Buffer queuePropsBuff = physicalDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean graphicsQueue = (props.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get graphics Queue family index");
            }
            return index;
        }
    }
    ...
}
```

The `GraphicsQueue`class just calls its parent constructor (`Queue` class) with the appropriate queue family index. That index is obtained through the call to the `getGraphicsQueueFamilyIndex`method. In this method, we iterate over the queue families to check if the queue family has the `VK_QUEUE_GRAPHICS_BIT` flag. 

## Render modifications

Now that we have finished our `PhysicalDevice`, `Device`, `Surface` and `Queue` classes, we can use them in our `Render` class. We will add new attributes:

```java
public class Render {
    ...
    private Device device;
    private Queue.GraphicsQueue graphQueue;
    ...
    private PhysicalDevice physicalDevice;
    private Surface surface;
    ...
}
```

They will be instantiated in the `init` method, and released in the `cleanup` method as with the case of the `instance` attribute:

```java
public class Render {
    ...
    public void cleanup() {
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void init(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
    }
    ...
```

That's all for this chapter, we are slowly defining the classes that we need in order to render something. We have still a long road ahead of us, but i hope the pieces will start to make sense soon.

[Next chapter](../chapter-04/chapter-04.md)