# Vulkan Memory Allocator and specialization constants

This will be a short chapter where we will introduce the VMA library which will help us with Vulkan memory allocation. Additionally, we will also introduce storage buffers.

You can find the complete source code for this chapter [here](../../booksamples/chapter-12).

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

We will create a new class named `MemoryAllocator` to handle the initialization of the VMA library. This class is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.util.vma.Vma.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class MemoryAllocator {

    private final long vmaAllocator;

    public MemoryAllocator(Instance instance, PhysicalDevice physicalDevice, VkDevice vkDevice) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            VmaVulkanFunctions vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), vkDevice);

            VmaAllocatorCreateInfo createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .instance(instance.getVkInstance())
                    .device(vkDevice)
                    .physicalDevice(physicalDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAllocator = pAllocator.get(0);
        }
    }

    public void cleanUp() {
        vmaDestroyAllocator(vmaAllocator);
    }

    public long getVmaAllocator() {
        return vmaAllocator;
    }
}
```

The constructor instantiates a VMA allocator by setting up a `VmaAllocatorCreateInfo` structure. In this structure we setup the device and physical device handles and a `VmaVulkanFunctions` structure which provides the Vulkan functions references that this library will use. The `VmaAllocatorCreateInfo` structure also defines a `flags` attribute to configure the behavior of the allocator. In our case, we will just use default values. The `MemoryAllocator` class also defines the classical methods to free the resources, to get the device used to instate the allocator and the handle to the VMA allocator itself.

We will create an instance of the `MemoryAllocator` class after we have created a `Device`, in the constructor of that class. We will also invoke the `cleanup` method when the associated `Device` instance is destroyed:

```java
public class Device {
    ...
    public Device(Instance instance, PhysicalDevice physicalDevice) {
            vkDevice = new VkDevice(pp.get(0), physicalDevice.getVkPhysicalDevice(), deviceCreateInfo);

            memoryAllocator = new MemoryAllocator(instance, physicalDevice, vkDevice);
        }
    }

    public void cleanup() {
        Logger.debug("Destroying Vulkan device");
        memoryAllocator.cleanUp();
        vkDestroyDevice(vkDevice, null);
    }
    ...
}
```

The `Device` class is first created in the `Render` class, since the `Device` constructor has been modified, we need to update also the `Render` class:

```java
public class Render {
    ...
    public Render(Window window, Scene scene) {
        ...
        device = new Device(instance, physicalDevice);
        ...
    }
    ...
}
```

The next step is to modify the `VulkanBuffer` class to use the VMA library. We will start with the class attributes:

```java
public class VulkanBuffer {

    private final long allocation;
    private final long buffer;
    private final Device device;
    private final PointerBuffer pb;
    private final long requestedSize;
    
    private long mappedMemory;
    ...
}
```

The `allocation` attribute is a handle to the allocated memory, which will be used later on to refer to that block and to perform the map and unmap operations. This removes the need to get track of the allocated size and the memory handle. We store now a reference to the `memoryAllocator` instance to be used in other methods. Let's review the changes in the constructor:

```java
public class VulkanBuffer {
    ...
    public VulkanBuffer(Device device, long size, int bufferUsage, int memoryUsage,
                        int requiredFlags) {
        this.device = device;
        requestedSize = size;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .requiredFlags(requiredFlags)
                    .usage(memoryUsage);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateBuffer(device.getMemoryAllocator().getVmaAllocator(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            buffer = lp.get(0);
            allocation = pAllocation.get(0);
            pb = MemoryUtil.memAllocPointer(1);
        }
    }
    ...
}
```

The constructor has split the old parameter `usage` flag into two: `bufferUsage` to control the buffer usage characteristics and `memoryUsage` to tune the memory usage. Buffer creation information is almost identical with the exception of the utilization of the `bufferUsage` parameter. This will used to specify if the buffer will be used for a shader uniform (`VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT`), as a transient buffer, etc. To allocate the buffer memory using the VMA library we need to create a  `VmaAllocationCreateInfo` structure which is defined by the following attributes:

- `requiredFlags`: This will control the memory requirements (For example if we are using the `VK_MEMORY_PROPERTY_HOST_COHERENT_BIT` flag). It can have a value equal to `0` if this is specified in other way.
- `usage`: This will instruct the intended usage for this buffer. For example if it can be accessed by the host (`VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT`) or not (`VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT`).

After that, we call the `vmaCreateBuffer` function which creates the Vulkan buffer, allocates the memory for it and binds the buffer to the allocated memory. The rest of the method of the `VulkanBuffer` class that need also to be modified are shown below:

```java
public class VulkanBuffer {
    ...
    public void cleanup() {
        pb.free();
        unMap();
        vmaDestroyBuffer(device.getMemoryAllocator().getVmaAllocator(), buffer, allocation);
    }
    ...
    public long map() {
        if (mappedMemory == NULL) {
            vkCheck(vmaMapMemory(device.getMemoryAllocator().getVmaAllocator(), allocation, pb),
                    "Failed to map allocation");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    public void unMap() {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(device.getMemoryAllocator().getVmaAllocator(), allocation);
            mappedMemory = NULL;
        }
    }
}
```

We need to modify the way the buffer resources are freed. Since the buffer and the associated memory are created in a single call, we can now free them by just calling the `vmaDestroyBuffer` function. Map and unmap operations also need to call VMA functions, `vmaMapMemory` for mapping the memory and `vmaUnmapMemory` for unmapping.

The next class to be modified is the `Texture` one. This class creates a buffer to store the texture image contents. Since the `VulkanBuffer` class constructor has been modified, we need to update the `createStgBuffer` to correctly specify the usage flags.

```java
public class Texture {
    ...
    private void createStgBuffer(Device device, ByteBuffer data) {
        ...
        stgBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        ...
    }
    ...
}
```

The class `VulkanModel` class needs also to be updated with small changes due to the changes in the `VulkanBuffer` constructor:

```java
public class VulkanModel {
    ...
    private static TransferBuffers createIndicesBuffers(Device device, ModelData.MeshData meshData) {
        ...
        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        ...
    }

    private static TransferBuffers createVerticesBuffers(Device device, ModelData.MeshData meshData) {
        ...
        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        ...
    }
    ...
}
```

As in the previous cases, we need to update the `GeometryRenderActivity` due to the `VulkanBuffer` class constructor modifications. (Changes are self-explanatory).


```java
public class GeometryRenderActivity {
    ...
    private void createDescriptorSets(int numImages) {
        ...
        projMatrixUniform = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        ...
        materialsBuffer = new VulkanBuffer(device, (long) materialDescriptorSetLayout.getMaterialSize() * engineProps.getMaxMaterials(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        ...
        for (int i = 0; i < numImages; i++) {
            ...
            viewMatricesBuffer[i] = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            ...
        }
    }
    ...
}
```

## Using storage buffers

Up to now, we have been using an array of uniforms to access light sources in the shaders. That array needs to have a fixed size, at compile time, so we need to maintain two constants, one in Java code and one in shader code, that model the same information, the maximum number of lights. If we forget to update on of these two constants we will be out of sync. Instead of using an array of uniforms, we will be using a storage buffer. Storage buffers are used for large data, and also do not need to know their size in the shader.


The way we access those buffers directly is through buffer storage descriptor sets. These descriptor sets are linked directly to a buffer and allow read and write operations. Therefore, we need to define a new descriptor set layout type for them:
```java
public abstract class DescriptorSetLayout {
    ...
    public static class StorageDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public StorageDescriptorSetLayout(Device device, int binding, int stage) {
            super(device, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, binding, stage);
        }
    }
    ...
}
```

After that, we can define a new class that will be used to instantiate the storage buffer descriptor sets. As in previous cases, we just need to define a new class that inherit from `SimpleDescriptorSet` as an inner class of the `DescriptorSet` class, which basically sets is type to the `VK_DESCRIPTOR_TYPE_STORAGE_BUFFER` value:
```java
public abstract class DescriptorSet {
    ...
    public static class StorageDescriptorSet extends SimpleDescriptorSet {

        public StorageDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout,
                                    VulkanBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                    buffer.getRequestedSize());
        }
    }
    ...
}
```

Let's review now the changes required in the light fragment shader (`lighting_fragment.glsl`):

```glsl
...
layout(set = 1, binding = 0) readonly buffer Lights {
    Light lights[];
} lights;

layout(set = 2, binding = 0) uniform SceneUniform  {
    vec4 ambientLightColor;
    uint numLights;
} sceneUniform;

layout(set = 3, binding = 0) uniform ProjUniform  {
    mat4 invProjectionMatrix;
} projUniform;
...
void main() {
    ...
    for (uint i = 0U; i < sceneUniform.numLights; i++)
    {
        ...
    }
    ...
    vec3 ambient = sceneUniform.ambientLightColor.rgb * albedo * ao;
    ...    
}
```

We have separate light information into two data structures:

- The first one is modelled with an storage buffer and holds only light sources information. You can see that we do not use the `uniform` keyword but `readonly buffer`. You can model the data inside an storage however you want, in our case we will model as chunks of `Light` structures, but it could contain floats, matrices, etc. As you can see we do not need to specify a fixed size.
- The second one is still an uniform and contains small information which makes no sense to model it using an storage buffer, such as ambient light color and the number of lights.

Since we have created an additional descriptor set, we need to update the `projUniform` set number.

Finally, we need to modify the `LightingRenderActivity` class to hold the changes in these descriptor sets and to populate properly the uniforms and the storage buffer.

```java
public class LightingRenderActivity {
    ...
    private DescriptorSet.StorageDescriptorSet[] lightsDescriptorSets;
    ...
    private VulkanBuffer[] sceneBuffers;
    private DescriptorSet.UniformDescriptorSet[] sceneDescriptorSets;
    ...
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    ...
    public void cleanup() {
        storageDescriptorSetLayout.cleanup();
        ...
        Arrays.asList(sceneBuffers).forEach(VulkanBuffer::cleanup);
    }
    ...
    private void createDescriptorPool(List<Attachment> attachments) {
        ...
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(swapChain.getNumImages(), VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        ...
    }

    private void createDescriptorSets(List<Attachment> attachments, int numImages) {
        ...
        storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                attachmentsLayout,
                storageDescriptorSetLayout,
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
        };
        ...
        lightsDescriptorSets = new DescriptorSet.StorageDescriptorSet[numImages];
        sceneDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsDescriptorSets[i] = new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout,
                    lightsBuffers[i], 0);
            sceneDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    sceneBuffers[i], 0);
        }
    }
    ...
}
```

As you can see we need to create new buffers and descriptor sets for the lights and scene light information. We will create as many as swap chain images we have since we can be modifying their values while being rendered. We need to create the new storage descriptor set layout and properly define the number of storage buffers (`VK_DESCRIPTOR_TYPE_STORAGE_BUFFER`) when creating the descriptor pool. In the `createUniforms` method we will create also the storage buffers, please pay attention that the usage flag needs to be set to `VK_BUFFER_USAGE_STORAGE_BUFFER_BIT`.

```java
public class LightingRenderActivity {
    ...
    private void createUniforms(int numImages) {
        invProjBuffer = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

        lightsBuffers = new VulkanBuffer[numImages];
        sceneBuffers = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.INT_LENGTH * 4 + GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                    GraphConstants.VEC4_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            
            sceneBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.VEC4_SIZE * 2, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        }
    }
    ...
}
```

Finally, we need to update the binding of the descriptor sets and properly update the buffers while rendering:

```java
public class LightingRenderActivity {
    ...
    public void preRecordCommandBuffer(int idx) {
        ...
            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, attachmentsDescriptorSet.getVkDescriptorSet())
                    .put(1, lightsDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, sceneDescriptorSets[idx].getVkDescriptorSet())
                    .put(3, invProjMatrixDescriptorSet.getVkDescriptorSet());
        ...
    }

    public void prepareCommandBuffer() {
        ...
        updateLights(scene.getAmbientLight(), scene.getLights(), scene.getCamera().getViewMatrix(),
                lightsBuffers[idx], sceneBuffers[idx]);
    }
    ...

    private void updateLights(Vector4f ambientLight, Light[] lights, Matrix4f viewMatrix,
                              VulkanBuffer lightsBuffer, VulkanBuffer sceneBuffer) {
        // Lights
        long mappedMemory = lightsBuffer.map();
        ByteBuffer uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) lightsBuffer.getRequestedSize());

        int offset = 0;
        int numLights = lights != null ? lights.length : 0;
        for (int i = 0; i < numLights; i++) {
            Light light = lights[i];
            auxVec.set(light.getPosition());
            auxVec.mul(viewMatrix);
            auxVec.w = light.getPosition().w;
            auxVec.get(offset, uniformBuffer);
            offset += GraphConstants.VEC4_SIZE;
            light.getColor().get(offset, uniformBuffer);
            offset += GraphConstants.VEC4_SIZE;
        }
        lightsBuffer.unMap();

        // Scene Uniform
        mappedMemory = sceneBuffer.map();
        uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) sceneBuffer.getRequestedSize());

        ambientLight.get(0, uniformBuffer);
        offset = GraphConstants.VEC4_SIZE;
        uniformBuffer.putInt(offset, numLights);

        sceneBuffer.unMap();
    }
}
```

[Next chapter](../chapter-13/chapter-13.md)