# Chapter 10 - Vulkan Memory Allocator

This will be a short chapter where we will introduce the VMA library which will help us with Vulkan memory allocation.

You can find the complete source code for this chapter [here](../../booksamples/chapter-10).

## Vulkan Memory Allocator (VMA)

[Vulkan Memory Allocator (VMA)](https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator) is a library that will help us to allocate memory in Vulkan in an easier en more efficient way. The advantages that this library provides, as stated in the Github page, are:

- Reduction of boilerplate code.
- Separation of elements that should be managed together (memory and buffers).
- Memory type selection is complex and needs to be adapted to different GPUs.
- Allocation if large chunks of memory is much more efficient than allocating small chunks individually.

IMHO, the biggest advantages are the last ones. VMA helps you in selecting the most appropriate type of memory and hides the complexity of managing large buffers to accommodate individual allocations while preventing fragmentation. In addition to that, VMA does not prevent you to still managing allocation in the pure Vulkan way in case you need it.

In order to use VMA library the following dependencies need to be added to the `pom.xml` file:

```xml
<?xml version="1.0" encoding="UTF-8"?>
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>org.lwjgl</groupId>
            <artifactId>lwjgl-vma</artifactId>
            <version>${lwjgl.version}</version>
        </dependency>
        ...
        <dependency>
            <groupId>org.lwjgl</groupId>
            <artifactId>lwjgl-vma</artifactId>
            <version>${lwjgl.version}</version>
            <classifier>${native.target}</classifier>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
```

We will create a new class named `MemAlloc` to handle the initialization of the VMA library. This class is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;
import static org.vulkanb.eng.graph.vk.VkUtils.vkCheck;

public class MemAlloc {

    private final long vmaAlloc;

    public MemAlloc(Instance instance, PhysDevice physDevice, Device device) {
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), device.getVkDevice());

            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.getVkInstance())
                    .vulkanApiVersion(VK_API_VERSION_1_3)
                    .device(device.getVkDevice())
                    .physicalDevice(physDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAlloc = pAllocator.get(0);
        }
    }

    public void cleanUp() {
        vmaDestroyAllocator(vmaAlloc);
    }

    public long getVmaAlloc() {
        return vmaAlloc;
    }
}
```

The constructor instantiates a VMA allocator by setting up a `VmaAllocatorCreateInfo` structure. In this structure we setup the device and physical device handles and a `VmaVulkanFunctions` structure which provides the Vulkan functions references that this library will use. The `VmaAllocatorCreateInfo` structure also defines a `flags` attribute to configure the behavior of the allocator. In our case, we will just use default values. The `MemAlloc` class also defines the classical methods to free the resources, to get the device used to instate the allocator and the handle to the VMA allocator itself. It is very important to properly set the `vulkanApiVersion` to `VK_API_VERSION_1_3`. If you
forget to use this, since we are using Vulkan 1.3, you will find strange issues when allocating buffers.

We will create an instance of the `MemAlloc` in the `VkCtx` class:

```java
public class VkCtx {
    ...
    private final MemAlloc memAlloc;
    ...
    public VkCtx(Window window) {
        ...
        memAlloc = new MemAlloc(instance, physDevice, device);
    }

    public void cleanup() {
        memAlloc.cleanUp();
        ...
    }
    ...
    public MemAlloc getMemAlloc() {
        return memAlloc;
    }
    ...
}
```

The next step is to modify the `VkBuffer` class to use the VMA library. We will start with the class attributes:

```java
public class VkBuffer {

    private final long allocation;
    private final long buffer;
    private final PointerBuffer pb;
    private final long requestedSize;
    ...
}
```

The `allocation` attribute is a handle to the allocated memory, which will be used later on to refer to that block and to perform the map and unmap operations. This removes the need to get track of the allocated size and the memory handle. Let's review the changes in the constructor:

```java
public class VkBuffer {
    ...
    public VkBuffer(VkCtx vkCtx, long size, int bufferUsage, int vmaUsage, int vmaFlags, int reqFlags) {
        requestedSize = size;
        mappedMemory = NULL;
        try (var stack = MemoryStack.stackPush()) {
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(vmaUsage)
                    .flags(vmaFlags)
                    .requiredFlags(reqFlags);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateBuffer(vkCtx.getMemAlloc().getVmaAlloc(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            buffer = lp.get(0);
            allocation = pAllocation.get(0);
            pb = MemoryUtil.memAllocPointer(1);
        }
    }
    ...
}
```

The constructor has split the old parameter `usage` flag into two: `bufferUsage` to control the buffer usage characteristics and `vmaUsage`
to tune the memory usage. Buffer creation information is almost identical with the exception of the utilization of the `bufferUsage` parameter.
To allocate the buffer memory using the VMA library we need to create a  `VmaAllocationCreateInfo` structure which is defined by the following attributes:

- `requiredFlags`: This will control the memory requirements (For example if we are using the `VK_MEMORY_PROPERTY_HOST_COHERENT_BIT` flag). It can have a value equal to `0` if this is specified in other way.
- `usage`: This will instruct the intended usage for this buffer. For example if it should be accessed only by the GPU (`VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE`) or the CPU (`VMA_MEMORY_USAGE_AUTO_PREFER_HOST`). Regarding this attribute, the recommended approach
is to set it always to `VMA_MEMORY_USAGE_AUTO` and let VMA manage it for us.

After that, we call the `vmaCreateBuffer` function which creates the Vulkan buffer, allocates the memory for it and binds the buffer to the allocated memory. The rest of the method of the `VkBuffer` class that need also to be modified are shown below:

```java
public class VkBuffer {
    ...
    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pb);
        unMap(vkCtx);
        vmaDestroyBuffer(vkCtx.getMemAlloc().getVmaAlloc(), buffer, allocation);
    }

    public void flush(VkCtx vkCtx) {
        vmaFlushAllocation(vkCtx.getMemAlloc().getVmaAlloc(), allocation, 0, VK_WHOLE_SIZE);
    }
    ...
    public long map(VkCtx vkCtx) {
        if (mappedMemory == NULL) {
            vkCheck(vmaMapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation, pb), "Failed to map buffer");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unMap(VkCtx vkCtx) {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation);
            mappedMemory = NULL;
        }
    }
}
```

We need to modify the way the buffer resources are freed. Since the buffer and the associated memory are created in a single call, we can now free them by just calling the `vmaDestroyBuffer` function. Map and unmap operations also need to call VMA functions, `vmaMapMemory` for mapping the memory and `vmaUnmapMemory` for un-mapping. We have added a new method to flush the contents of CPU mapped buffers if we do not want to
use the coherent flag to do it automatically for us.

The next class to be modified is the `Texture` one. This class creates a buffer to store the texture image contents. Since the `VkBuffer` class constructor has been modified, we need to update the `createStgBuffer` to correctly specify the usage flags.

```java
public class Texture {
    ...
    private void createStgBuffer(VkCtx vkCtx, ByteBuffer data) {
        ...
        stgBuffer = new VkBuffer(vkCtx, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        ...
    }
    ...
}
```

In this case, since it is a buffer that needs to be accessed by both CPU and GPU, we use `VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT`
flag. 

The class `ModelsCache` class needs also to be updated with small changes due to the changes in the `VkBuffer` constructor:

```java
public class ModelsCache {
    ...
    private static TransferBuffer createIndicesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream idxInput)
            throws IOException {
        ...
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                0, 0);
        ...
    }

    private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream vtxInput)
            throws IOException {
        ...
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                0, 0);
        ...
    }
    ...
}
```

We need to update also the `MaterialsCache` class due to the changes in the `VkBuffer` constructor:

```java
public class MaterialsCache {
    ...
    public void loadMaterials(VkCtx vkCtx, List<MaterialData> materials, TextureCache textureCache, CmdPool cmdPool,
                              Queue queue) {
        ...
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        materialsBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                0, 0);
        ...
    }
    ...
}
```

In this case we also use the same flags.

We need to update also the `VkUtils`class which have two utility methods to use CPU-GPU accessible buffers:

```java
public class VkUtils {
    ...
    public static VkBuffer createHostVisibleBuff(VkCtx vkCtx, long buffSize, int usage, String id, DescSetLayout layout) {
        var buff = new VkBuffer(vkCtx, buffSize, usage, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        Device device = vkCtx.getDevice();
        DescSet descSet = vkCtx.getDescAllocator().addDescSet(device, id, layout);
        descSet.setBuffer(device, buff, buff.getRequestedSize(), layout.getLayoutInfo().binding(),
                layout.getLayoutInfo().descType());
        return buff;
    }

    public static VkBuffer[] createHostVisibleBuffs(VkCtx vkCtx, long buffSize, int numBuffs, int usage,
                                                    String id, DescSetLayout layout) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        VkBuffer[] result = new VkBuffer[numBuffs];
        descAllocator.addDescSets(device, id, numBuffs, layout);
        DescSetLayout.LayoutInfo layoutInfo = layout.getLayoutInfo();
        for (int i = 0; i < numBuffs; i++) {
            result[i] = new VkBuffer(vkCtx, buffSize, usage, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            DescSet descSet = descAllocator.getDescSet(id, i);
            descSet.setBuffer(device, result[i], result[i].getRequestedSize(), layoutInfo.binding(), layoutInfo.descType());
        }
        return result;
    }
    ...
}
```

The `Image` class needs to be also highly modified, since the allocation mechanisms for images and the associated buffers change a lot
when using VMA.

```java
public class Image {

    private final long allocation;
    private final int format;
    private final int mipLevels;
    private final long vkImage;
    ...
```

First, we no longer will ned keep track of the allocated memory but we will need to keep an allocation handle, as in the case of the
`VkBuffer` class. The constructor now looks like this:

```java
public class Image {
    ...
    public Image(VkCtx vkCtx, ImageData imageData) {
        try (var stack = MemoryStack.stackPush()) {
            this.format = imageData.format;
            this.mipLevels = imageData.mipLevels;

            VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .extent(it -> it
                            .width(imageData.width)
                            .height(imageData.height)
                            .depth(1)
                    )
                    .mipLevels(mipLevels)
                    .arrayLayers(imageData.arrayLayers)
                    .samples(imageData.sampleCount)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(imageData.usage);

            var allocCreateInfo = VmaAllocationCreateInfo.calloc(1, stack)
                    .get(0)
                    .usage(VMA_MEMORY_USAGE_AUTO)
                    .flags(imageData.memUsage)
                    .priority(1.0f);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateImage(vkCtx.getMemAlloc().getVmaAlloc(), imageCreateInfo, allocCreateInfo, lp, pAllocation, null),
                    "Failed to create image");
            vkImage = lp.get(0);
            allocation = pAllocation.get(0);
        }
    }
    ...
}
```

We now use a `VmaAllocationCreateInfo` structure with `VMA_MEMORY_USAGE_AUTO` usage value and the memory usage flags. The `vmaCreateImage`
function will take care of allocating and binding the memory.

We need to update also the `cleanup` method to use `vmaDestroyImage`:

```java
public class Image {
    ...
    public void cleanup(VkCtx vkCtx) {
        vmaDestroyImage(vkCtx.getMemAlloc().getVmaAlloc(), vkImage, allocation);
    }
    ...
}
```

Also we need to include an additional attribute in the `ImageData` inner class to store allocation flags:

```java
public class Image {
    ...
    public static class ImageData {
        ...
        private int memUsage;
        ...
        public ImageData() {
            ...
            memUsage = 0;
        }
        ...
        public ImageData memUsage(int memUsage) {
            this.memUsage = memUsage;
            return this;
        }
        ...
    }
    ...
}
```

Finally, we update the `Attachment` class. In this case we use `VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT` class which is specially
recommended for large allocations such as full screen images.

```java
public class Attachment {
    ...
    public Attachment(VkCtx vkCtx, int width, int height, int format, int usage) {
        var imageData = new Image.ImageData().width(width).height(height).
                usage(usage | VK_IMAGE_USAGE_SAMPLED_BIT).
                format(format)
                .memUsage(VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT);
        ...
    }
    ...
}
```

[Next chapter](../chapter-11/chapter-11.md)