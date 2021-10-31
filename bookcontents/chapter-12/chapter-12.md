# Vulkan Memory Allocator and specialization constants

This will be a short chapter where we will introduce the VMA library which will help us with Vulkan memory allocation. Additionally, we will also introduce specialization constants, which will allow us to modify constants in shaders at run time.

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
            pb = PointerBuffer.allocateDirect(1);
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
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        ...
    }
    ...
}
```

Finally, as in the previous cases, we need to update the `GeometryRenderActivity` and `LightingRenderActivity` classes due to the `VulkanBuffer` class constructor modifications. (Changes are self-explanatory)

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

```java
public class LightingRenderActivity {
    ...
    private void createUniforms(int numImages) {
        invProjBuffer = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

        lightsBuffers = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.INT_LENGTH * 4 + GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                    GraphConstants.VEC4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        }
    }
    ...
}
```

## Specialization constants

Specialization constants allows us to modify constants used in the shaders at run-time, when the pipeline that uses those shaders is created. For example, by now we have a constant, in the fragment shader of  the lighting phase which defines the maximum number of lights. This is defined also in the Java code, to properly size the buffers that will hold lights information. The problem with this, is that, if we want to change that constant, we need to do it in the Java code and the shader code at the same time. If we use specialization constants, we can set this value at run-time, keeping always the Java and the shader code perfectly in sync. This will also not impact the performance, since it is resolved when creating the pipeline.

Specialization constants are defined using a `VkSpecializationInfo` structure, which defines the structure of the data that will be used as constants (basically, its size and a numerical identifier associated to each value so we can refer to in the shaders). In our case, we will use specialization constants to modify at run-time the maximum number of lights. In order to handle this, we will create a new class named `LightSpecConstants` under the `org.vulkanb.eng.graph.lighting` package (since they will be using in the lighting phase). The class is defined like this:

```java
package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class LightSpecConstants {

    private ByteBuffer data;

    private VkSpecializationMapEntry.Buffer specEntryMap;
    private VkSpecializationInfo specInfo;

    public LightSpecConstants() {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH);
        data.putInt(GraphConstants.MAX_LIGHTS);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(1);
        specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
                .offset(0);

        specInfo = VkSpecializationInfo.calloc();
        specInfo.pData(data)
                .pMapEntries(specEntryMap);
    }

    public void cleanup() {
        MemoryUtil.memFree(specEntryMap);
        specInfo.free();
        MemoryUtil.memFree(data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
```

First, we create a buffer that will hold the specialization constants data, that is, the maximum number of lights. Then we need to create one `VkSpecializationMapEntry` for each specialization constant (only one in our case). The `VkSpecializationMapEntry` defines  the numerical identifier used by the constant, the size of the data and the offset in the buffer that holds the data for all the constants. With all that information, we create the `VkSpecializationInfo` structure.

The `VkSpecializationInfo` structure needs to be associated to the shader that will use it (if any), therefore we need to modify the `ShaderModule` and `ShaderModuleData` records, so that  information is available when creating the pipeline.

```java
public class ShaderProgram {
    ...
    public record ShaderModule(int shaderStage, long handle, VkSpecializationInfo specInfo) {
    }

    public record ShaderModuleData(int shaderStage, String shaderSpvFile, VkSpecializationInfo specInfo) {
        public ShaderModuleData(int shaderStage, String shaderSpvFile) {
            this(shaderStage, shaderSpvFile, null);
        }
    }
}
```

In the `ShaderProgram` class constructor, we just transfer the specialization constants set in the `ShaderModuleData`  record (if any) to the `ShaderModule` record (which will be used in the pipeline creation):

```java
public class ShaderProgram {
    ...
    public ShaderProgram(Device device, ShaderModuleData[] shaderModuleData) {
        try {
            this.device = device;
            int numModules = shaderModuleData != null ? shaderModuleData.length : 0;
            shaderModules = new ShaderModule[numModules];
            for (int i = 0; i < numModules; i++) {
                byte[] moduleContents = Files.readAllBytes(new File(shaderModuleData[i].shaderSpvFile()).toPath());
                long moduleHandle = createShaderModule(moduleContents);
                shaderModules[i] = new ShaderModule(shaderModuleData[i].shaderStage(), moduleHandle,
                        shaderModuleData[i].specInfo());
            }
        } catch (IOException excp) {
            Logger.error("Error reading shader files", excp);
            throw new RuntimeException(excp);
        }
    }
    ...
}
```

We are ready now to use that information in the `Pipeline` class:

```java
public class Pipeline {
    ...
    public Pipeline(PipelineCache pipelineCache, Pipeline.PipeLineCreationInfo pipeLineCreationInfo) {
        ...
            ShaderProgram.ShaderModule[] shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
            int numModules = shaderModules.length;
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderProgram.ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(shaderModule.shaderStage())
                        .module(shaderModule.handle())
                        .pName(main);
                if (shaderModule.specInfo() != null) {
                    shaderStages.get(i).pSpecializationInfo(shaderModule.specInfo());
                }
            }
        ...
    }
    ...
}
```

As it is shown, if we define specialization constants for a specific shader, we just initialize the `pSpecializationInfo` with the associated `VkSpecializationInfo` structure.

Using specialization constants in the shaders is pretty simple, we just need to assign the identifier associated to the `VkSpecializationMapEntry` entry  defined when creating the `VkSpecializationInfo` structure. For example, in our case, in the lighting fragment shader, the specialization constant for the maximum number of lights is defined like this:

```glsl
layout (constant_id = 0) const int MAX_LIGHTS = 10;
```

We just need to add the `constant_id` parameter. If the specialization constant is not defined, the default value present in the shader will be used.

The final step is to use the `LightSpecConstants` class in the `LightingRenderActivity` class:

```java
public class LightingRenderActivity {
    ...
    private SpecializationConstants specializationConstants;
    ...
    public LightingRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache,
                                  Attachment[] attachments, Scene scene) {
        ...
        lightSpecConstants = new LightSpecConstants();
        ...
    }

    public void cleanup() {
        ...
        lightSpecConstants.cleanup();
        ...
    }
    ...
    private void createShaders() {
        ...
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV,
                                lightSpecConstants.getSpecInfo()),
                });
    }
    ...
}
```

[Next chapter](../chapter-13/chapter-13.md)