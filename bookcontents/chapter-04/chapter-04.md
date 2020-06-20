# Swap chain

In this chapter we will continue our road to render on the screen. We will describe in detail a concept that has been mentioned in previous chapters, the swap chain.

You can find the complete source code for this chapter [here](../../booksamples/chapter-04).

## Swap chain

If you recall from previous chapters, rendering to the screen is an optional feature in Vulkan. The capability to present rendering results to a surface is provided by the swap chain. A swap chain is an array of images that can be use as the destination of render operations and that can be presented to a surface.

Swap chain creation will be encapsulated in a class named `SwapChain`.

![UML Diagram](yuml-01.svg)

Let's start with its attributes and the constructor:

```java
private static final Logger LOGGER = LogManager.getLogger();
private Device device;
private ImageView[] imageViews;
private SurfaceFormat surfaceFormat;
private long vkSwapChain;

public SwapChain(Device device, Surface surface, Window window, int requestedImages, boolean vsync) {
    LOGGER.debug("Creating Vulkan SwapChain");
    this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {

            PhysicalDevice physicalDevice = device.getPhysicalDevice();

            int numImages = calcNumImages(surfCapabilities, requestedImages);
```

We will be identifying the purpose of the different attributes while we go through the code. The first thing we do to calculate the number of images that our swap chain will have. You may be wondering why do we need more than one image? The answer is to increase performance, while an image is being presented, we may be using another one to render the results of the next frame. We need to have several in order to parallelize the tasks and use both the CPU and GPU at their maximum capacity. The most common use cases employ two images (double buffering) or three (triple buffering), as in the figure below.

![Swap chain images](swapchain.svg)

The figure above represents the triple buffer case. While image #1 is used for presentation we have ready another one, image #2, ready to be presented. In parallel, we are using image #3 for rendering. Triple buffering is commonly used to avoid frame rate dropout, when we cannot guarantee that a frame will be ready between v-syncs (You can read more about this [here](https://github.com/KhronosGroup/Vulkan-Samples/blob/master/samples/performance/swapchain_images/swapchain_images_tutorial.md)).

Our `SwapChain` class constructor has a parameter named `requestedImages` which is used to express the desired number of images our swap chain should have. The `calcNumImages` method tries to accommodate that request to the surface limits defined by the surface capabilities that we obtained at the beginning of the constructor. The definition of the `calcNumImages` is as follows:

```java
private int calcNumImages(VkSurfaceCapabilitiesKHR surfCapabilities, int requestedImages) {
    int maxImages = surfCapabilities.maxImageCount();
    int minImages = surfCapabilities.minImageCount();
    int result = minImages;
    if (maxImages != 0) {
        result = Math.min(requestedImages, maxImages);
    }
    result = Math.max(result, minImages);
    LOGGER.debug("Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]",
            requestedImages, result, maxImages, minImages);
    return result;
}
```

The first thing we do is retrieve the minimum and maximum number of images that our surface supports. If we get a value of `0` for the maximum number of images, this means that there is no limit. The rest of the code is basically to try to stick with the  requested value if it's within the maximum-minimum range.

Let's go back again to our constructor. The next thing to do is calculate the image format and the color space of our swap chain images:

```java
        this.surfaceFormat = calcSurfaceFormat(physicalDevice, surface);
```

Let's review the definition of the `calcSurfaceFormat` method:

```java
private SurfaceFormat calcSurfaceFormat(PhysicalDevice physicalDevice, Surface surface) {
    int imageFormat;
    int colorSpace;
    try (MemoryStack stack = MemoryStack.stackPush()) {
        IntBuffer ip = stack.mallocInt(1);
        vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.getVkPhysicalDevice(),
                surface.getVkSurface(), ip, null), "Failed to get the number surface formats");
        int numFormats = ip.get(0);
        if (numFormats <= 0) {
            throw new RuntimeException("No surface formats retrieved");
        }
        VkSurfaceFormatKHR.Buffer surfaceFormats = VkSurfaceFormatKHR.callocStack(numFormats, stack);
        vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.getVkPhysicalDevice(),
                surface.getVkSurface(), ip, surfaceFormats), "Failed to get surface formats");

        imageFormat = VK_FORMAT_B8G8R8A8_UNORM;
        colorSpace = surfaceFormats.get(0).colorSpace();
        for (int i = 0; i < numFormats; i++) {
            VkSurfaceFormatKHR surfaceFormatKHR = surfaceFormats.get(i);
            if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_UNORM &&
                    surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                imageFormat = surfaceFormatKHR.format();
                colorSpace = surfaceFormatKHR.colorSpace();
                break;
            }
        }
    }
    return new SurfaceFormat(imageFormat, colorSpace);
}
```

The first thing we do is retrieve the number of formats our surface supports by calling the `vkGetPhysicalDeviceSurfaceFormatsKHR` Vulkan function. As with many other Vulkan samples, we first call that function to get the total number of formats supported and then we create a buffer of structures, `VkSurfaceFormatKHR` in this case, to retrieve the data by calling the same function again. Once we have all that data, we iterate over the formats trying to check if `VK_FORMAT_B8G8R8A8_UNORM`(8 bits for RGBA channels normalized) and SRGB non linear color space are supported. `SurfaceFormat` is just a `record` which stores the image format and the color space.

It is turn again to go back to the constructor. We need to calculate now the extent of the images of the swap chain:

```java
        VkExtent2D swapChainExtent = calcSwapChainExtent(stack, window, surfCapabilities);
```

The `calcSwapChainExtent` method is defined like this: 

```java
public VkExtent2D calcSwapChainExtent(MemoryStack stack, Window window, VkSurfaceCapabilitiesKHR surfCapabilities) {
    VkExtent2D swapChainExtent = VkExtent2D.callocStack(stack);
    if (surfCapabilities.currentExtent().width() == 0xFFFFFFFF) {
        // Surface size undefined. Set to the window size if within bounds
        int width = Math.min(window.getWidth(), surfCapabilities.maxImageExtent().width());
        width = Math.max(width, surfCapabilities.minImageExtent().width());

        int height = Math.min(window.getHeight(), surfCapabilities.maxImageExtent().height());
        height = Math.max(height, surfCapabilities.minImageExtent().height());

        swapChainExtent.width(width);
        swapChainExtent.height(height);
    } else {
        // Surface already defined, just use that for the swap chain
        swapChainExtent.set(surfCapabilities.currentExtent());
    }
    return swapChainExtent;
}
```

If the surface has already defined its extent, we pick that. If it is not defined, we try to set the extent to the window size if it is within the ranges of minimum  and maximum supported sizes. We are now ready to create the swap chain by filling up the `VkSwapchainCreateInfoKHR` structure. Let's review the attributes of that structure:

- `sType`: The structure type, in this case `VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR`.
- `surface`: A reference to our surface.
- `minImageCount`: The minimum number of images that this swap chain should use. This is the value that we have got calling the `calcNumImages` method.
- `imageFormat`: The format of the images of this swap chain (for example RGB with 8 bits per channel). 
- `imageColorSpace`: The color space of the images of this swap chain (for example sRGB).
- `imageExtent`: The size (in pixels)  of the images of this swap chain.
- `imageArrayLayers`: The number of views in a multi-view / stereo surface. In our case, since we are not creating a stereoscopic-3D application, this will set to `1`.
- `imageUsage`: Bitmask of `VkImageUsageFlagBits` describing the intended usage of the (acquired) swap chain images. That is, what operations we will be doing with them. In our case we will be rendering these images to the surface, so we will use the `VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT` flag. That is, we will be outputting colors. There are other usages such as the ones used for depth buffers or for transferring operations.
- `imageSharingMode`: This controls how the images can be accessed from queues. Remember that in Vulkan we will record the render operations to be executed in `CommandBuffer`''s  that will be queued. Those commands will be de-queued for being executed asynchronously. There are two possible modes:
  - `VK_SHARING_MODE_CONCURRENT`: Multiple queue families can access the images concurrently.  
  - `VK_SHARING_MODE_EXCLUSIVE`: Only one queue family can access the images at a time. This is the most performant mode. Since we will be using graphics queue families we will using this mode.
- `preTransform`: Describes the transform, relative to the presentation engine’s natural orientation, applied to the image content prior to presentation (such as rotation of certain angles). In our case, we will use the current transformation mode.
- `compositeAlpha`: Indicates the alpha compositing mode to use when this surface is composited together with other surfaces on certain window systems. In our case we will use the `VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR` value.  This basically ignores the alpha component when compositing (no transparencies to be used for the surface).
- `clippped`: Specifies whether the Vulkan implementation is allowed to discard rendering operations that affect regions of the surface that are not visible. It is recommended to set this to parameter to `true`.
- `presentMode`: Specifies the presentation mode the Swap chain will use.

Presentation modes need to be explained in detail. Vulkan defines the following presentation modes:

- `VK_PRESENT_MODE_IMMEDIATE_KHR`: The generated images are transferred immediate to the screen without waiting for a vertical blanking period. This may produce tearing in some cases.
- `VK_PRESENT_MODE_FIFO_KHR`: Images are presented when a vertical blanking occurs. Images ready to be presented are internally queued using a FIFO schema (First Int First Out), which means that new images are appended to the end of the queue. This is the only mode that is guaranteed to be supported. If the queue is full, the application blocks. Tearing is not observed in this case.
- `VK_PRESENT_MODE_FIFO_RELAXED_KHR`: Similar to the previous mode. In the case that the queue fills up, the application waits, but the queued images during this waiting period are presented without waiting for the vertical blanking. This may produce tearing in some cases.
- `VK_PRESENT_MODE_MAILBOX_KHR`:  Similar to the `VK_PRESENT_MODE_IMMEDIATE_KHR` mode, but in the case that the queue is full, the last image queued is overwritten by the new one.

In our case, the `Swapchain` class constructor receives a parameter, named `vsync` which we will use to specify if we want to wait for the vertical blanking or not. If `vsync` is true we will use the `VK_PRESENT_MODE_FIFO_KHR`. In this case our frame rate will be, as a maximum, the maximum refresh rate of our screen. If `vsync` is false we will opt for the `VK_PRESENT_MODE_IMMEDIATE_KHR` presentation mode.

We are now ready to create the swap chain at last in our constructor with the following code:

```java
        VkSwapchainCreateInfoKHR vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.callocStack(stack)
                .sType(KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface.getVkSurface())
                .minImageCount(numImages)
                .imageFormat(this.surfaceFormat.imageFormat())
                .imageColorSpace(this.surfaceFormat.colorSpace())
                .imageExtent(swapChainExtent)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(surface.getVkSurfaceCapabilities().currentTransform())
                .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .clipped(true);
        if (vsync) {
            vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR);
        } else {
            vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
        }
        LongBuffer lp = stack.mallocLong(1);
        vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.getVkDevice(), vkSwapchainCreateInfo, null, lp),
                "Failed to create swap chain");
        this.vkSwapChain = lp.get(0);
```

## Swap chain images

The next step is to retrieve the images of the Swap chain. We will use them when rendering to specify where we are rendering to. Those images have already been created when we created the swap chain, we just one to retrieve their handles. However, in Vulkan we cannot just use the images, we need an indirect element to access them. This element is called Image View. Before going on, it is important to precisely define the concepts involved in handling images in Vulkan. In order to have an image that can be accessed by shaders in Vulkan we need:

- A `Buffer` which contains the raw data of the image, its contents. A `Buffer` is just a linear array of data.
- An `Image`  which basically defines the metadata associated to the `Buffer`. That is, the image format, its dimensions, etc.
- An `Image View` , which specifies how we are going to use it., which parts of that image can be accessed, etc.  Well, it is just a view over the image. As it has been said before, the images for our swap chain have already been created, we just need to create the associated image views.

![UML Diagram](yuml-02.svg)

This is done at the end of the constructor y calling the `createImageViews`method:

```java
        this.imageViews = createImageViews(stack, device, this.vkSwapChain, this.surfaceFormat.imageFormat);
    }
}       
```

The definition of that method is as follows:

```java
private ImageView[] createImageViews(MemoryStack stack, Device device, long swapChain, int format) {
    ImageView[] result;

    IntBuffer ip = stack.mallocInt(1);
    vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, null),
            "Failed to get number of surface images");
    int numImages = ip.get(0);

    LongBuffer swapChainImages = stack.mallocLong(numImages);
    vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, swapChainImages),
            "Failed to get surface images");

    result = new ImageView[numImages];
    for (int i = 0; i < numImages; i++) {
        result[i] = new ImageView(device, swapChainImages.get(i), format, VK_IMAGE_ASPECT_COLOR_BIT, 1);
    }

    return result;
}
```

The first thing we do is retrieve the **actual** number of images that our swap chain has. But wait, we had already specified this when we created the swap chain, why we do need to retrieve that again? The answer is that we created the swap chain with a desired number of images, but the Vulkan driver may have returned a different number. This is why we need to call the  `vkGetSwapchainImagesKHR` function to retrieve the number of images. After that, we call that same function again to retrieve the handles to the images themselves.
Now we iterate over the images to create new `ImageView` instances. The `ImageView` class encapsulates the creation and disposal of Vulkan image views. Its constructor is defined like this:

```java
public ImageView(Device device, long vkImage, int format, int aspectMask, int mipLevels) {
    this.device = device;
    try (MemoryStack stack = MemoryStack.stackPush()) {
        LongBuffer lp = stack.mallocLong(1);
        VkImageViewCreateInfo viewCreateInfo = VkImageViewCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(vkImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format)
                .subresourceRange(it -> it
                        .aspectMask(aspectMask)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1));

        vkCheck(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                "Failed to create image view");
        this.vkImageView = lp.get(0);
    }
}
```

In order to create a Image View we need to fill up a `VkImageViewCreateInfo` structure. This structure is defined by the following attributes:

- `sType`: The structure type: `VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO`.
- `image`: A reference to the image handle that this view refers to. In our case it will be one of the swap chain images.
- `viewType`: The type of image view. In our case they will be 2D images.
- `format`: The format of the image. In this case we just use the format of the underlying swap chain images.
- `subresourceRange`: This parameter allow us to select a specific range of the underlying image. We can select a specific set of mipmap levels or layers (in the case of array of layers). In this case, we can control the maximum mipmap level (through the `mipLevels`argument), and we stick to 1 layer. Regarding the aspects, for this specific case, we will get the color aspect (for example there are some other aspect for depth images).

With the `VkImageViewCreateInfo` structure filled up, we just need to call the `vkCreateImageView` to get a handle to the the Image View. The rest of the class is just a *getter* for the handle and the `cleanUp` method to free the resources.

```java
public void cleanUp() {
    vkDestroyImageView(this.device.getVkDevice(), this.vkImageView, null);
}

public long getVkImageView() {
    return vkImageView;
}
```

Now we can use the `Swapchain` class in our `Render`class:

```java
public class Render {
    // ....
    private SwapChain swapChain;

    public void cleanUp() {
        this.swapChain.cleanUp();
        // ....
    }

    public void init(Window window) {
        // ... 
        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
    }
```

We have also modified also the `EngineProperties` class to read a new property to configure the usage of  vsync:

```java
public class EngineProperties {
...
    private boolean vSync;
...
    private EngineProperties() {
            ...
            this.vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
            ...
    }
```

[Next chapter](../chapter-05/chapter-05.md)