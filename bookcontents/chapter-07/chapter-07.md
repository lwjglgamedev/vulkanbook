# Going 3D

In this chapter we will be rendering a 3D model (a rotating cube). Although our models did have a z-coordinate, the output attachments we were using only dealt with colors, depth values were not handled at all. We need to define a new attachment to store depth information, a depth buffer. In addition to that, we will add support for window resizing.
You can find the complete source code for this chapter [here](../../booksamples/chapter-07).

## Depth Image

The first thing we must do is to create a depth image. In the case of the swap chain, images were already there, but in this case we need to allocate by ourselves. In order to handle that task we will create a new class named `Image`. The constructor starts like this:

```java
public Image(Device device, int width, int height, int format, int usage, int mipLevels, int sampleCount) {
    this.device = device;
    try (MemoryStack stack = MemoryStack.stackPush()) {
        this.format = format;
        this.mipLevels = mipLevels;

        VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.callocStack(stack)
               .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
               .imageType(VK_IMAGE_TYPE_2D)
               .format(format)
               .extent(it -> it
                        .width(width)
                        .height(height)
                        .depth(1)
               )
               .mipLevels(mipLevels)
               .arrayLayers(1)
               .samples(sampleCount)
               .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
               .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
               .tiling(VK_IMAGE_TILING_OPTIMAL)
               .usage(usage);
```

The constructor receives the following parameters:

- `width` and `height`: The size of the image.

- `format`:  Describes the format of the texel blocks that compose the image.

- `usage`:  Describes the intended usage that this image will have.

- `mipLevels`:  Describes the levels of detail available for this image (more on this in later chapters).

- `sampleCount`:  Specifies the number of texels per sample (more on this in later chapters).

Besides storing some parameters as attributes of the class, the first thing we do is create a structure required to create an image named `VkImageCreateInfo`. The attributes that we are using are:

- `sType`:  The classical type of the structure. In this case we use the value `VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO`.

- `imageType`:  It specifies the dimensions of the the image. In our case we will be using regular 2D dimensions, so we set to the value:  `VK_IMAGE_TYPE_2D`.  Three dimensions images (`VK_IMAGE_TYPE_3D`) are like a set of slices of 2D textures and are used for volumetric effects or for scientific or medical visualizations (like MRIs).  One dimension textures are define by the value `VK_IMAGE_TYPE_1D`.

- `format`: Described above, format of the texel blocks that compose the image.

- `extent`:  It is the size of the image. In this case, the structure needs to support 3D images, so it includes the depth. For 2D images we just set it to `1`.

- `mipLevels`:  Already described in the constructor's parameters description.

- `arrayLayers`:  Images can be an array of layers. This is different than a 3D image. A 3D image contains data that is referred to the three axis. An array of layers are a set of 2D images indexed by a layer number. In our case we will just set it to `1`. 

- `samples`: Already described in the constructor's parameters description (`campleCount`).

- `initialLayout`:  This is the initial layout of the image. We just set it to `VK_IMAGE_LAYOUT_UNDEFINED` . If a transition to another layout is required it will need to be done later on. (This depends on the use case for the image, this is why we do not perform the transition here).

- `sharingMode`: It specifies if this resource will be shared by more than a single queue family at a time (`VK_SHARING_MODE_CONCURRENT`) or not (`VK_SHARING_MODE_EXCLUSIVE`).

- `tiling`: It specifies the tiling arrangement of the texel blocks in memory.  In our case, we chose to use the optimal value, so that the texels are laid out in the best format for each GPU.

- `usage`:  Already described in the constructor's parameters description.

Now we can create the image by invoking the `vkCreateImage` Vulkan function:

```java
        LongBuffer lp = stack.mallocLong(1);
        vkCheck(vkCreateImage(device.getVkDevice(), imageCreateInfo, null, lp), "Failed to create image");
        this.vkImage = lp.get(0);
```

Then we need to allocate the memory associated to that image. As in the case of buffers, that image is just a handle, we need to manually allocate the memory that will host the contents for the image by ourselves.  The first step is to get the memory requirements by calling the `vkGetImageMemoryRequirements` function:

```java
        // Get memory requirements for this object
        VkMemoryRequirements memReqs = VkMemoryRequirements.callocStack(stack);
        vkGetImageMemoryRequirements(device.getVkDevice(), this.getVkImage(), memReqs);
```

With that information we can populate the `VkMemoryAllocateInfo` structure which contains the information to perform the memory allocation.

```java
        // Select memory size and type
        VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                        memReqs.memoryTypeBits(), 0));
```

Again, the code is similar as the one used with the buffers, once we have got the requirements, we set the memory size and select the adequate memory type index (obtained by calling the `memoryTypeFromProperties` from the `VulkanUtils` class). After that we can finally allocate the memory and bind it to the image:

```java
        // Allocate memory
        vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
        this.vkMemory = lp.get(0);

        // Bind memory
        vkCheck(vkBindImageMemory(device.getVkDevice(), this.getVkImage(), this.getVkMemory(), 0),
                "Failed to bind image memory");
    }
}
```

The rest of the methods of this class are the `cleanUp` to free resources and some *getters* to obtain the image handle, the associated memory, the format of the image and the mip levels.

```java
public void cleanUp() {
    vkDestroyImage(this.device.getVkDevice(), this.vkImage, null);
    vkFreeMemory(this.device.getVkDevice(), this.vkMemory, null);
}

public int getFormat() {
    return this.format;
}

public int getMipLevels() {
    return mipLevels;
}

public long getVkImage() {
    return this.vkImage;
}

public long getVkMemory() {
    return this.vkMemory;
}
```
