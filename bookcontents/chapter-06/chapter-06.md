# Drawing a triangle

In this chapter we will be finally rendering a shape, developing the required classes to load data to the GPU and using the graphics pipeline. We will start, as in the previous chapters, by explaining the elements that we will need later on to use together to draw something to the screen.

You can find the complete source code for this chapter [here](../../booksamples/chapter-06).

## Buffers

If we want to display 3D models, we need first to load all the vertices information that define them (positions, texture coordinates, indices, etc.). All that information needs to be stored in buffers accessible by the GPU. A buffer in Vulkan is basically a bunch of bytes that can be used for whatever we want, from storing vertices to storing data used for computation. As usual, we will create a new class named `VulkanBuffer` to manage them. Let's examine the constructor:

```java
public VulkanBuffer(Device device, long size, int usage, int reqMask) {
     this.device = device;
     this.requestedSize = size;
     try (MemoryStack stack = MemoryStack.stackPush()) {
         VkBufferCreateInfo bufferCreateInfo = VkBufferCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(size)
                .usage(usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
         LongBuffer lp = stack.mallocLong(1);
         vkCheck(vkCreateBuffer(device.getVkDevice(), bufferCreateInfo, null, lp), "Failed to create vertices buffer");
         this.buffer = lp.get(0);
```

The constructor just receives the `device` that will be used to create this buffer, its size, a parameter named `usage` which will state what this buffer should be used for and a bit mask. This last parameter is use to set the requested memory properties that the data associated to this buffer should use. We will review how these two last parameters are used later on. In order to create a Buffer we need to setup a structure named `VkBufferCreateInfo`, which defines the following attributes:

- `sType`: It shall have the `VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO` value.
- `size`: The number of bytes that the buffer will hold.
- `usage`: It specifies the allowed usages of the buffer. We can specify that this buffer can be used for transfer commands (for example to uses as source in buffer copy operations), as a destination transfer, for uniforms. etc. This will be received in the constructor of the `VulkanBuffer` class through an argument with the same name.
- `sharingMode`: If set to `VK_SHARING_MODE_EXCLUSIVE`, it can only be accessed by a queue family at a time. `VK_SHARING_MODE_CONCURRENT` allows the buffer contents to be accessed by more than one queue family at a time. Concurrent mode incurs on performance penalties, so we will just use exclusive mode.

With that structure we can invoke the `vkCreateBuffer`function to create the buffer handle. It is important to remark, that this call does not allocate the memory for the buffer, we just create the handle, we will need to manually allocate that memory and associate that to the buffer later on. Therefore the next thing we do is to retrieve the memory requirements of the new created buffer, by calling the `vkGetBufferMemoryRequirements` function.

```java
        VkMemoryRequirements memReqs = VkMemoryRequirements.mallocStack(stack);
        vkGetBufferMemoryRequirements(device.getVkDevice(), this.buffer, memReqs);
```

The next thing to do is to allocate the memory. Again, in order to achieve that, we need to setup a structure named `VkMemoryAllocateInfo`, which defines the following attributes:

- `sType`: It shall have the `VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO` value.

- `allocationSize`: It will hold the size of the memory to be allocated in bytes.

- `memoryTypeIndex`: It will hold the memory type index to be used. The index refers to the memory types available in the device.
  
  ```java
        VkMemoryAllocateInfo memAlloc = VkMemoryAllocateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(VulkanUtils.memoryTypeFromProperties(device.getPhysicalDevice(),
                         memReqs.memoryTypeBits(), reqMask));
  ```

In order to fill the `memoryTypeIndex` we call the `memoryTypeFromProperties`method from the `VulkanUtils` class, which is defined like this:

```java
public static int memoryTypeFromProperties(PhysicalDevice physDevice, int typeBits, int reqsMask) {
    int result = -1;
    VkMemoryType.Buffer memoryTypes = physDevice.getVkMemoryProperties().memoryTypes();
    for (int i = 0; i < VK_MAX_MEMORY_TYPES; i++) {
        if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqsMask) == reqsMask) {
            result = i;
            break;
        }
        typeBits >>= 1;
    }
    if (result < 0) {
        throw new RuntimeException("Failed to find memoryType");
    }
    return result;
}
```

The `typeBits` attribute is a bit mask which defines the supported memory types of the physical device. A bit set to `1` means that the type of memory (associated to that index) is supported. The `reqMask` attribute is the type of memory that we need (for example if that memory will be accessed only by the GPU or also by the application). This method basically iterates over all the memory types, checking if that memory index (first condition) is supported by the device and if that it meets the requested type (second condition). Now we can go back to the `VulkanBuffer` constructor and invoke the `vkAllocateMemory` to allocate the memory. After that we can get the finally allocated size and get a handle to that chunk of memory.

```java
        vkCheck(vkAllocateMemory(device.getVkDevice(), memAlloc, null, lp), "Failed to allocate memory");
        this.allocationSize = memAlloc.allocationSize();
        this.memory = lp.get(0);
```

Now we need to link the allocated memory with the buffer handle, this is done by calling the `vkBindBufferMemory` function:

```java
        vkCheck(vkBindBufferMemory(device.getVkDevice(), this.buffer, this.memory, 0), "Failed to bind buffer memory");
    }
}
```

The constructor is now finished. The next methods are the usual `cleanUp`method and some getters for the properties that define the buffer:

```java
public void cleanUp() {
    vkDestroyBuffer(this.device.getVkDevice(), this.buffer, null);
    vkFreeMemory(this.device.getVkDevice(), this.memory, null);
}

public long getAllocationSize() {
    return allocationSize;
}

public long getBuffer() {
    return buffer;
}

public long getMemory() {
    return memory;
}

public long getRequestedSize() {
    return requestedSize;
}
```

## Vertex description

We have now created the buffers required to hold the data for vertices, the next step is to describe to Vulkan the format of that data. In order to do that, we will create a new class named `VertexBufferStructure` which will be used by Vulkan to know hot to extract that data from the underlying buffer. The class starts like this:

```java
public class VertexBufferStructure {

    private static final int NUMBER_OF_ATTRIBUTES = 1;
    private static final int POSITION_COMPONENTS = 3;
    private VkPipelineVertexInputStateCreateInfo vi;
    private VkVertexInputAttributeDescription.Buffer viAttrs;
    private VkVertexInputBindingDescription.Buffer viBindings;

    public VertexBufferStructure() {
        this.viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        this.viBindings = VkVertexInputBindingDescription.calloc(1);
        this.vi = VkPipelineVertexInputStateCreateInfo.calloc();
```

At the beginning of the constructor we create several structures required for Vulkan to understand how hour vertices will be used:

- `VkVertexInputAttributeDescription`: It is used to describe each vertex attribute.
- `VkVertexInputBindingDescription`: It is used to specify if the boundaries of each vertex "package" and how it will consumed (per instance or per vertex).
- `VkPipelineVertexInputStateCreateInfo`: This will hold the vertices format that will be used in a Vulkan pipeline (more on this later). It will hold the previous two structures.

Let's start by filling up the attributes description:

```java
        int i = 0;
        // Position
        this.viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);
```

We need to fill up as many attribute descriptors as input variables describing the input we will have in our shaders. In our case, by now, we will just use one attribute for the position, so we only include one description. The attributes are:

- `binding`: The binding number associated to this vertex description. This will be used later on the shaders so we can use several vertices descriptions independently associated to different binding points. 
- `format`: The format of this attribute, in this case we are saying that we will be using three 32 bit signed floats.
- `offset`: The relative offset in bytes for this attribute when processing one vertex element. The buffer will contain many vertices. Each of them can have different attributes (positions, texture coordinates). This offset refers to the position of this attribute to the beginning of each vertex element. This is the first attribute so it should be `0`.

Now is turn to fill up the binding description:

```java
        this.viBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * GraphConstants.FLOAT_LENGTH)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
```

The attributes are:

- `binding`: The same meaning as in the vertices attributes description.
- `stride`: The distance in bytes from two consecutive elements in the buffer. In our case, we are using 32 bit floats for the positions (4 bytes each) and three position components (x, y, z).
- `inputRate`: It specifies the rate at which vertex attributes are pulled from the underlying buffer. It can have two possible values:
  - `VK_VERTEX_INPUT_RATE_VERTEX`: Values will be extracted for each vertex index. That is, when consuming an index we will get one element form the buffer. This is the regular case when drawing a mesh, the vertex data is associated to a specific index and will be consumed accordingly.
  - `VK_VERTEX_INPUT_RATE_INSTANCE`: Values will be extracted for each instance index. This will be the case used for instanced rendering, where with a single buffer defining the common attributes (mesh definition) we can draw many models with a single draw call. The buffer will hold common data for all the models and per-instance data, therefore we will need to combine the two types of input rate.

To finalize the constructor, we glue all the previous structures in the  `VkPipelineVertexInputStateCreateInfo`structure:

```java
        this.vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(this.viBindings)
                .pVertexAttributeDescriptions(this.viAttrs);
    }
```

The rest of the methods are the usual suspects, the `cleanUp` one  to free the resources and another one to get the `VkPipelineVertexInputStateCreateInfo` reference:

```java
    public void cleanUp() {
        this.vi.free();
        this.viBindings.free();
        this.viAttrs.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
```

## Loading the data

We have created the the structures that will hold the data for our models (`VulkanBuffer`) and the ones that define their format (`VulkanMesh`). We are now ready to load the data into the GPU. In essence, we need to load the data into two buffers, one for the positions of the vertices and another one for the indices of the triangle coordinates that wll be used to actually form the triangles. The class that will hold these buffers and the methods to populate them is named `VulkanMesh`. The basic structure of this class is quite simple:

```java
package org.vulkanb.eng.graph.vk;

// Imports...

public class VulkanMesh {

    private String id;
    private VulkanBuffer indicesBuffer;
    private int indicesCount;
    private VulkanBuffer verticesBuffer;

    public VulkanMesh(String id, VulkanBuffer verticesBuffer, VulkanBuffer indicesBuffer, int indicesCount) {
        this.id = id;
        this.verticesBuffer = verticesBuffer;
        this.indicesBuffer = indicesBuffer;
        this.indicesCount = indicesCount;
    }
    ....

    public void cleanUp() {
        this.indicesBuffer.cleanUp();
        this.verticesBuffer.cleanUp();
    }

    public String getId() {
        return id;
    }

    public VulkanBuffer getIndicesBuffer() {
        return this.indicesBuffer;
    }

    public int getIndicesCount() {
        return indicesCount;
    }

    public VulkanBuffer getVerticesBuffer() {
        return this.verticesBuffer;
    }
    ....
}
```

This class just stores the buffers associated to the vertices positions and the indices, their `getters` and a `cleanUp` method. The interest of this class resides in its static methods which will be used to create those buffers. This class provides a `public` `static` method named `loadMeshes` which will be used to create a set of `VulkanMesh` instances using the model raw data positions and indices). That raw data is encapsulated in a `record`named `MeshData` and is defined like this:

```java
package org.vulkanb.eng.scene;

public record MeshData(String id, float[]positions, int[]indices) {
}
```

The `loadMeshes` method will do the will return as many `VulkanMesh`instances as `MeshData` instances received. It will encapsulate all the buffers and data copy creations operations. As it has been shown before, each `VulkanMesh`instance has two buffers, one for positions and another one for the indices. These buffers will be used by the GPU while rendering but we need to access them form the CPU in order to load the data into them. We could use buffers that are accessible from both the CPU and the GPU, but the performance would be worse than buffers that can only used by the GPU. So, how do we solve this? The answer is by using intermediate buffers:

1. We first create an intermediate buffer (or staging buffer) that can be accessed both by the CPU and the GPU. This will be our source buffer.
2. We create another buffer that can be accessed only from the GPU. This will be our destination buffer.
3. We load the data into the source buffer (the intermediate buffer) from our application (CPU).
4. We copy the source buffer into the destination buffer.
5. We destroy the source buffer (the intermediate buffer). It is not needed anymore.

The purpose of the `loadMeshes` method is to perform these actions for the positions and indices buffers for each of the `MeshData` instances. It should be used at the initialization stage as a bulk loading mechanism (more efficient). Copying from one buffer to another implies submitting a transfer command to a queue and waiting for it to complete. Instead of submitting these operations one by one, we can record all these commands into a single `CommandBuffer`, submit them just once and also wait once for the commands to be finished. This will be much more efficient than submitting small commands one at a time.

The `loadMeshes` method starts like this:

```java
public static VulkanMesh[] loadMeshes(CommandPool commandPool, Queue queue, MeshData[] meshDataList) {
    int numMeshes = meshDataList != null ? meshDataList.length : 0;
    VulkanMesh[] meshes = new VulkanMesh[numMeshes];

    try (MemoryStack stack = MemoryStack.stackPush()) {
        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();

        VulkanBuffer[] positionTransferBuffers = new VulkanBuffer[numMeshes];
        VulkanBuffer[] indicesTransferBuffers = new VulkanBuffer[numMeshes];
```

The method creates the result array with the same size as the list of `MeshData` used as a parameter. Then, it creates a new `CommandBuffer` which will be used to record the copy operations that involve the different buffers used and start the recording. We define also two arrays of `VulkanBuffer` which will be used as intermediate buffers used for position and indices. Then we iterate over the different `MeshData` instances:

```java
        for (int i = 0; i < numMeshes; i++) {
            MeshData meshData = meshDataList[i];
            TransferBuffers verticesBuffers = createVerticesBuffers(device, meshData);
            TransferBuffers indicesBuffers = createIndicesBuffers(device, meshData);

            positionTransferBuffers[i] = verticesBuffers.srcBuffer();
            indicesTransferBuffers[i] = indicesBuffers.srcBuffer();

            meshes[i] = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(), indicesBuffers.dstBuffer(),
                    meshData.indices().length);
            recordTransferCommand(cmd, verticesBuffers);
            recordTransferCommand(cmd, indicesBuffers);
        }
```

The `createVerticesBuffers` method creates the intermediate buffer, loads the positions into it and also creates the final (GPU accessible only) buffer. The source and destination buffers are returned encapsulated into a `record` named `TransferBuffers`.

```java
private record TransferBuffers(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer) {
}
```

The same operation is done for the indices buffers. We the create a `VulkanMesh` instance using the destination buffers and record the copy commands for both buffers by calling the `recordTransferCommand` method. Let's review the `createVerticesBuffers` method:

```java
private static TransferBuffers createVerticesBuffers(Device device, MeshData meshData) {
    float[] positions = meshData.positions();
    int numPositions = positions.length;
    int bufferSize = numPositions * GraphConstants.FLOAT_LENGTH;

    VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
            VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    try (MemoryStack stack = MemoryStack.stackPush()) {
        PointerBuffer pp = stack.mallocPointer(1);
        vkCheck(vkMapMemory(device.getVkDevice(), srcBuffer.getMemory(), 0, srcBuffer.getAllocationSize(), 0, pp),
                "Failed to map memory");

        FloatBuffer data = pp.getFloatBuffer(0, numPositions);
        data.put(positions);

        vkUnmapMemory(device.getVkDevice(), srcBuffer.getMemory());
    }

    return new TransferBuffers(srcBuffer, dstBuffer);
}
```

The intermediate buffer is created with the `VK_BUFFER_USAGE_TRANSFER_SRC_BIT` flag as its usage parameter. With this flag we state that this buffer can be used as the source of a transfer command. For the `reqMask` attribute we use the combination of two flags:

- `VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT`: This means that the memory allocated by this buffer can be mapped and accessed by the CPU. This is what we need in order to populate with the mesh data.
- `VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT`: This means that we do not need to execute flushing commands when the CPU writes to this buffer or vice versa.

The destination buffer is created with the `VK_BUFFER_USAGE_TRANSFER_DST_BIT` as its usage parameter. With this flag we state that this buffer can used as the destination of a transfer command. We also set the flag `VK_BUFFER_USAGE_VERTEX_BUFFER_BIT` since it will be used for handling vertices data. For the `reqMask` attribute we use the `VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT` flag which states that the memory allocated by this buffer will be used by the GPU.

Once the buffers have been created we need to populate the source buffer. In order to do that, we need to map that memory in order to get a pointer to it so we can upload the data. This is done by calling the `vkMapMemory` function. Now we have a pointer to the memory of the buffer which we will use to load the positions. After we have finished copying the data to the source buffer we call the `vkUnmapMemory`.

The definition of the `createIndicesBuffers` is similar:

```java
private static TransferBuffers createIndicesBuffers(Device device, MeshData meshData) {
    int[] indices = meshData.indices();
    int numIndices = indices.length;
    int bufferSize = numIndices * GraphConstants.FLOAT_LENGTH;

    VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
            VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    try (MemoryStack stack = MemoryStack.stackPush()) {
        PointerBuffer pp = stack.mallocPointer(1);
        vkCheck(vkMapMemory(device.getVkDevice(), srcBuffer.getMemory(), 0, srcBuffer.getAllocationSize(), 0, pp),
                "Failed to map memory");

        IntBuffer data = pp.getIntBuffer(0, numIndices);
        data.put(indices);

        vkUnmapMemory(device.getVkDevice(), srcBuffer.getMemory());
    }

    return new TransferBuffers(srcBuffer, dstBuffer);
}
```

The major difference is that, in this case, the usage flag is `VK_BUFFER_USAGE_INDEX_BUFFER_BIT` for the destination buffer.

## Graphics pipeline overview

A graphics pipeline is a model which describes the sets required to render a scene into a screen. In Vulkan this is modeled using a data structure. This structure defines several parameters to control the certain steps (fixed  steps) allowing setting up programs (called shaders) to control the  execution of other steps (programmable steps). The following picture depicts Vulkan graphics pipeline.

![Graphics pipeline](yuml-01.svg)

Description of the stages (NOTE: graphics pipeline in Vulkan can also work in mesh shading mode, in this case we are referring to primitive shading mode. More information in the Vulkan [specification]((https://www.khronos.org/registry/vulkan/specs/1.1-extensions/html/vkspec.html#pipelines):

- Input Assembler: It is the responsible of assembling vertices to form graphics primitives such as triangles.
- Vertex shader: In this stage we transform the vertices received as inputs (positions, normals, texture coordinates, etc.). It is a programmable stage.
- Tesselation and geometry shader stages can generate  multiple primitives form a single input primitive or modify them received from previous stages.  These stages are also programmable through shaders.
- Rasterization: This stage transform primitives into fragments (pixels) that can be displayed on a 2D image.
- Fragment shader: Processes the fragments from the rasterization stage determining the values that will be written into the frame buffer output attachments. This is also a programmable stage which usually outputs the color for each pixel.
- Blending: Controls how different fragments can be mixed over the same pixel handling aspects such as transparencies and color mixing.

One important topic to understand when working with Vulkan pipelines is  that they are almost immutable. Unlike OpenGL, we can't modify at run time the properties of a graphics pipeline. Almost any change that we want to make implies the creation of a new pipeline. In OpenGL it is common to modify ay runtime certain parameters that control how transparencies are handled (blending) or if the depth-testing is enabled. We can modify those parameters at run time with no restrictions. (The reality is that under the hood, our driver is switching between pipelines definitions that meet those settings). In Vulkan, however, we will need to define multiple pipelines if we ant to change those settings while rendering ans switch between them manually. 

## Shaders

Prior to start defining the pipeline we will write the code for handling shaders. As it has just been said before, shaders allow us to control, using code, the pipeline stages that are programmable. If you come from OpenGL, you are used to program the shaders in GLSL language, if you come from DirectX you will use HLSL language. In this case, Vulkan uses a different approach and uses a binary format called SPIR-V. Both GLSL and HLSL are human readable languages, that imposes extra overhead on the drivers that need to parse those formats and convert their instructions into something that can be used by the GPU. Another issue with these formats is the different implementation behaviors that can arise when different vendors perform different assumptions, since the complexity of the parsers required leave room for different interpretations. Vulkan, by using a binary format, reduces the complexity on the drivers, reducing also the time required to load them. As a bonus, you can still develop your shaders in your favorite language (GLSL or HLSL) and transform them to SPIR-V using an external compiler. In this book, we will  use GLSL language, providing specific tools to automatically convert them to SPIR-V.

We will create a new class named `ShaderProgram` to manage shaders which is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class ShaderProgram {

    private static final Logger LOGGER = LogManager.getLogger();

    private Device device;
    private ShaderModule[] shaderModules;

    public ShaderProgram(Device device, ShaderModuleData[] shaderModuleData) {
        try {
            this.device = device;
            int numModules = shaderModuleData != null ? shaderModuleData.length : 0;
            this.shaderModules = new ShaderModule[numModules];
            for (int i = 0; i < numModules; i++) {
                byte[] moduleContents = Files.readAllBytes(new File(shaderModuleData[i].shaderSpvFile()).toPath());
                long moduleHandle = createShaderModule(moduleContents);
                this.shaderModules[i] = new ShaderModule(shaderModuleData[i].shaderStage(), moduleHandle);
            }
        } catch (IOException excp) {
            LOGGER.error("Error reading shader files", excp);
            throw new RuntimeException(excp);
        }
    }

    public void cleanUp() {
        for (ShaderModule shaderModule : this.shaderModules) {
            vkDestroyShaderModule(this.device.getVkDevice(), shaderModule.handle(), null);
        }
    }

    private long createShaderModule(byte[] code) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pCode = stack.malloc(code.length).put(0, code);

            VkShaderModuleCreateInfo moduleCreateInfo = VkShaderModuleCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(pCode);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateShaderModule(device.getVkDevice(), moduleCreateInfo, null, lp),
                    "Failed to create shader module");

            return lp.get(0);
        }
    }

    public ShaderModule[] getShaderModules() {
        return this.shaderModules;
    }

    public record ShaderModule(int shaderStage, long handle) {
    }

    public record ShaderModuleData(int shaderStage, String shaderSpvFile) {
    }
}
```

In our case, a `ShaderProgram` groups a set of shader modules (vertex shader, fragment shader) under a single class. The constructor, besides a reference to the a Vulkan `Device`, receives an array of `ShaderModuleData` instances, each of them defined by an `int` value the identifies the pipeline stage where this shader applies to (vertex, fragment, geometry, etc) and the path to the SPIR-V module file. The constructor iterates over the array of `ShaderModuleData` instances, loading the contents of the SPIR-V files and invoking the `createShaderModule` which creates the shader module in Vulkan getting a handle to it. The results are stored in another array. Those handles are the ones that will be used later on in the pipeline definition. The `cleanUp` method is used to free the allocated handles for the different modules by calling the `vkDestroyShaderModule` function. The `createShaderModule` method is responsible of creating the shader modules, It fill ups a `VkShaderModuleCreateInfo` structure with the SPIR-V byte code of the shader module and calls the `vkCreateShaderModule` function to retrieve the handle. Shader module handles are stored, along with the stage that should be applied using the `record` named `ShaderModule`.

Now we need to be able to compile from GLSL source code to SPIR-V. We need to add a new dependency to the POM file to use the `shaderc` bindings:

```xml
...
        <dependency>
            <groupId>org.lwjgl</groupId>
            <artifactId>lwjgl-shaderc</artifactId>
            <version>${lwjgl.version}</version>
        </dependency>
...
        <dependency>
            <groupId>org.lwjgl</groupId>
            <artifactId>lwjgl-shaderc</artifactId>
            <version>${lwjgl.version}</version>
            <classifier>${native.target}</classifier>
            <scope>runtime</scope>
        </dependency>
...
```

We do not want to compile the whole set of shaders every time we start the application, we just want to compile if  the source code changes. We will provide a utility method named `compileShaderIfChanged` in a separate class named `ShaderCompiler` which is defined like this:

```java
public static void compileShaderIfChanged(String glsShaderFile, int shaderType) {
    byte[] compiledShader;
    try {
        File glslFile = new File(glsShaderFile);
        File spvFile = new File(glsShaderFile + ".spv");
        if (!spvFile.exists() || glslFile.lastModified() > spvFile.lastModified()) {
            LOGGER.debug("Compiling [{}] to [{}]", glslFile.getPath(), spvFile.getPath());
            String shaderCode = new String(Files.readAllBytes(glslFile.toPath()));

            compiledShader = compileShader(shaderCode, shaderType);
            Files.write(spvFile.toPath(), compiledShader);
        } else {
            LOGGER.debug("Shader [{}] already compiled. Loading compiled version: [{}]", glslFile.getPath(), spvFile.getPath());
        }
    } catch (IOException excp) {
        throw new RuntimeException(excp);
    }
}
```

The method receives, through the `glsShaderFile` parameter, the path to the GLSL file and the type of shader. In this case, the `shaderType` parameter should be one of the defined by the `org.lwjgl.util.shaderc.Shaderc` class. This method checks if the GLSL file has changed (by comparing the date of the SPIR-V file vs the GLSL file) and compiles it by calling the `compileShader` method and writes the result to a file constructed with the same path bad adding the `.spv` extension. 

The `compileShader` method just invokes the `shaderc_result_get_compilation_status` from the `Shaderc` compiler binding provided by LWJGL.

```java
public static byte[] compileShader(String shaderCode, int shaderType) {
    long compiler = 0;
    long options = 0;
    byte[] compiledShader;

    try {
        compiler = Shaderc.shaderc_compiler_initialize();
        options = Shaderc.shaderc_compile_options_initialize();

        long result = Shaderc.shaderc_compile_into_spv(
                compiler,
                shaderCode,
                shaderType,
                "shader.glsl",
                "main",
                 options
        );

        if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
            throw new RuntimeException("Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
        }

        ByteBuffer buffer = Shaderc.shaderc_result_get_bytes(result);
        compiledShader = new byte[buffer.remaining()];
        buffer.get(compiledShader);
    } finally {
        Shaderc.shaderc_compile_options_release(options);
        Shaderc.shaderc_compiler_release(compiler);
    }

    return compiledShader;
}
```

## Pipeline

The next step is to write the code that supports graphic pipelines creation. Prior to talking about pipelines specifically, we will talk about the pipeline cache. As mentioned before, while working with Vulkan, it is very common to have multiple pipelines. Pipelines are almost immutable, so any variant on the setup of the different stage requires a new pipeline instance. In order to speed up pipeline creation Vulkan introduces the concept of the Pipeline cache. The cache allows more efficient pipeline creation by reusing the common parts between them and also between application executions. It is possible, for example, to persist the contents of the cache to disk, and load them at startup time in the next run of our application reducing the time required to create them. In our case, we will limit, at this time, the usage of the pipeline cache. By now we will just use it to speed up pipe line re-creation during application execution. We need to create a new class named `PipelineCache` which is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class PipelineCache {
    private static final Logger LOGGER = LogManager.getLogger();
    private Device device;
    private long vkPipelineCache;

    public PipelineCache(Device device) {
        LOGGER.debug("Creating pipeline cache");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCacheCreateInfo createInfo = VkPipelineCacheCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Error creating pipeline cache");
            this.vkPipelineCache = lp.get(0);
        }
    }

    public void cleanUp() {
        LOGGER.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(this.device.getVkDevice(), this.vkPipelineCache, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkPipelineCache() {
        return this.vkPipelineCache;
    }
}
```

This is a simple class, we just invoke the `vkCreatePipelineCache`to create the cache and provide the usual `getters` and `cleanUp` methods.

Now it is the turn to create the pipeline, which will be encapsulated in a new class named `Pipeline`. This class receives in a constructor a record which will be used to configure the pipe line creation. It is defined like this:

```java
public record PipeLineCreationInfo(long vkRenderPass, ShaderProgram shaderProgram, int numColorAttachments,
                                   VertexBufferStructure vertexBufferStructure) {
    public void cleanUp() {
        vertexBufferStructure.cleanUp();
    }
}
```

The `record` just stores the render pass handle, the list of shader modules, the number of color attachments and the definition of the vertices structure. It also provides a `cleanUp` method, that should be called after the associated pipeline has been create, to free the resources.

The constructor of the `Pipeline`class starts like this:

```java
public Pipeline(PipelineCache pipelineCache, Pipeline.PipeLineCreationInfo pipeLineCreationInfo) {
    LOGGER.debug("Creating pipeline");
    this.device = pipelineCache.getDevice();
    try (MemoryStack stack = MemoryStack.stackPush()) {
        LongBuffer lp = stack.mallocLong(1);

        ByteBuffer main = stack.UTF8("main");

        ShaderProgram.ShaderModule[] shaderModules = pipeLineCreationInfo.shaderProgram.getShaderModules();
        int numModules = shaderModules.length;
        VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.callocStack(numModules, stack);
        for (int i = 0; i < numModules; i++) {
            shaderStages.get(i)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(shaderModules[i].shaderStage())
                    .module(shaderModules[i].handle())
                    .pName(main);
        }
```

The first thing we do is to create as many `VkPipelineShaderStageCreateInfo` structures as shader modules we have. For each of them we set the stage that it should be applied to, the handle to the module itself and the name of the entry point of the shader for that stage (`pName`). Concerning the shader stages, we cannot use the same constants as when compiling the shaders, we need to use Vulkan constant, not the ones defined by `shaderc`. For example for a vertex shader we should use the `VK_SHADER_STAGE_VERTEX_BIT` constant and for fragment shaders we should use the `VK_SHADER_STAGE_FRAGMENT_BIT` value. After that, we set-up the input assembly stage:

```java
        VkPipelineInputAssemblyStateCreateInfo vkPipelineInputAssemblyStateCreateInfo =
                VkPipelineInputAssemblyStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                        .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
```

The input assembly stage take a set of vertices and produces a set of primitives. The primitives to be produced are defined in the `topology` attribute. In our case, we will generate a list of triangles (`VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST`). We could generate other types such as points (`VK_PRIMITIVE_TOPOLOGY_POINT_LIST`), lines (`VK_PRIMITIVE_TOPOLOGY_LINE_LIST`), triangle strips (`VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP`), etc. For our examples we can leave that parameter fixed to the `VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST` value. The next step is to define how many view ports and scissors we are going to use. The view port describes the region from the output attachments that we will be using for rendering (normally we will use the whole size of those buffers). The view port defines the transformation from normalized coordinates to screen coordinates. It is a transformation, the rendered image will be stretched or enlarged to fit the dimensions of the view port. The scissor defines a rectangle where outputs can be made, any pixel that lays out side that region will be discarded. Scissor are not transformations, they simply cut out regions that do not fit their dimensions. In our case, we will be using just one view port and one scissor (we need at least one). 

```java
        VkPipelineViewportStateCreateInfo vkPipelineViewportStateCreateInfo =
                VkPipelineViewportStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                        .viewportCount(1)
                        .scissorCount(1);
```

After that we configure the rasterization stage:

```java
        VkPipelineRasterizationStateCreateInfo vkPipelineRasterizationStateCreateInfo =
                VkPipelineRasterizationStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .cullMode(VK_CULL_MODE_NONE)
                        .frontFace(VK_FRONT_FACE_CLOCKWISE)
                        .lineWidth(1.0f);
```

Description of the parameters:

- `polygonMode`: It specifies how triangles should be rendered: In our case we want the triangles to be filled up with the color assigned in the fragments. For example, if we want to draw it as lines (as in OpenGL, the equivalent would be to use this line: `glPolygonMode( GL_FRONT_AND_BACK, GL_LINE )`)  we should use `VK_POLYGON_MODE_LINE`.
- `cullMode`: This is used if we want to apply culling (for example, not drawing triangles that are in the inner parts of models). By now we are not applying culling, but we can activate it according to the orientation of the vertices of the triangles.
- `frontFace`: It specifies how front face for culling is determined. In our case, we set to `VK_FRONT_FACE_CLOCKWISE`, that is, if the vertices are drawn in clockwise order they are considered as clock wise.
- `lineWidth`: It specifies the width of the rasterized fragments.

The next step is to define how multi-sampling will be done. Multi-sampling is used in anti-aliasing to reduce the artifacts produced by the fact that pixels are discrete elements which cannot perfectly model continuous elements. By taking multiple samples of adjacent fragments when setting the color of a pixel, borders are smoothed and the quality of the images is often better. This is done by creating a `VkPipelineMultisampleStateCreateInfo` structure. In this case we are not using multiple samples so we just set the number of samples to one bit (`VK_SAMPLE_COUNT_1_BIT`):

```java
        VkPipelineMultisampleStateCreateInfo vkPipelineMultisampleStateCreateInfo =
                VkPipelineMultisampleStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                        .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
```

The next step is to configure color blending. This stage allows combining the color of a fragment with the contents that already exists in that buffer. This allows to apply effects like transparencies:

```java
        VkPipelineColorBlendAttachmentState.Buffer blendAttState = VkPipelineColorBlendAttachmentState.callocStack(
                pipeLineCreationInfo.numColorAttachments(), stack);
        for (int i = 0; i < pipeLineCreationInfo.numColorAttachments(); i++) {
            blendAttState.get(i)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        }
        VkPipelineColorBlendStateCreateInfo colorBlendState =
                VkPipelineColorBlendStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                        .pAttachments(blendAttState);
```

We need first to configure the blending options for each of the output attachments through a buffer of `VkPipelineColorBlendAttachmentState` structures. By now, we will not be playing with the settings that support transparencies, we just need to setup the colors that will be enabled for writing by setting the `colorWriteMask` attribute. In our case we simply enable all the color channels. Then we need to group all those configurations on a `VkPipelineColorBlendStateCreateInfo` structure (this structure also defines other parameters to setup global blending settings).

We have said before that pipelines are almost immutable, there are only a few things that we can modify once the pipeline has been created. We can change a fixed set of things, such as the view port size, the scissor region size, the blend constants, etc. We need to specify the values that could be changed dynamically. In our case, we do not want to recreate the pipeline if the window is resized, so we need to create a `VkPipelineDynamicStateCreateInfo` structure which sets the dynamic states that will be applied to `VK_DYNAMIC_STATE_VIEWPORT`  and `VK_DYNAMIC_STATE_SCISSOR`. By setting this, the view port and scissor dimensions are not set in the pipeline creation, they need to be set when recording the commands. This is not so efficient at statically defining it in the pipeline, but we avoid re-creating the swap chain when resizing.

```java
        VkPipelineDynamicStateCreateInfo vkPipelineDynamicStateCreateInfo =
                VkPipelineDynamicStateCreateInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                        .pDynamicStates(stack.ints(
                                VK_DYNAMIC_STATE_VIEWPORT,
                                VK_DYNAMIC_STATE_SCISSOR
                        ));
```

While rendering we may to pass additional parameters to the shaders (for example by using uniforms), those parameters need to be associated to a binding point. Even though we are still not using those features, we need to create the structure that will hold these definitions:

```java
        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

        vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                "Failed to create pipeline layout");
        this.vkPipelineLayout = lp.get(0);
```

Now we have all the information required to create the pipeline. We just need to set upa buffer if `VkGraphicsPipelineCreateInfo` structures. It is a buffer, because can several pipelines with a single call to the the `vkCreateGraphicsPipelines` function. In our case, we will create them one by one:

```java
        VkGraphicsPipelineCreateInfo.Buffer pipeline = VkGraphicsPipelineCreateInfo.callocStack(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                    .pVertexInputState(pipeLineCreationInfo.vertexBufferStructure().getVi())
                .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                .pViewportState(vkPipelineViewportStateCreateInfo)
                .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                .pColorBlendState(colorBlendState)
                .pDynamicState(vkPipelineDynamicStateCreateInfo)
                .layout(this.vkPipelineLayout)
                .renderPass(pipeLineCreationInfo.vkRenderPass);

        vkCheck(vkCreateGraphicsPipelines(device.getVkDevice(), pipelineCache.getVkPipelineCache(), pipeline, null, lp),
                "Error creating graphics pipeline");
        this.vkPipeline = lp.get(0);
    }
}
```

The constructor is now finished. The rest of the methods of the class are the `cleanUp` method for destroying the resources and some **getters** to get the pipeline handle and its layout.

```java
public void cleanUp() {
    LOGGER.debug("Destroying pipeline");
    vkDestroyPipelineLayout(this.device.getVkDevice(), this.vkPipelineLayout, null);
    vkDestroyPipeline(this.device.getVkDevice(), this.vkPipeline, null);
}

public long getVkPipeline() {
    return this.vkPipeline;
}

public long getVkPipelineLayout() {
    return this.vkPipelineLayout;
}
```

## Using the pipeline

We are now ready to put all the pieces together and render something to the screen. We will start from our `Main` class. We will create a sample mesh in the `init` method which was previously empty:

```java
package org.vulkanb;
...
public class Main implements IAppLogic {
    ...
    @Override
    public void init(Window window, Scene scene, Render render) {
        MeshData meshData = new MeshData("Triangle", new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f},
                new int[]{0, 1, 2});
        render.loadMeshes(new MeshData[]{meshData});
    }
}
```

We create a new instance of the `MeshData` class that define the vertices of a triangle. Each mesh has an `String` which acts as an identifier. We will use that later on to link entities with the associated meshes. Mesh information also stores information about the indices. We invoke a new method in the `Render` class named `loadMeshes`. Let's review now the changes in that class:

```java
package org.vulkanb.eng.graph;
...
    private List<VulkanMesh> meshList;
...
    public void cleanUp() {
        ...
        this.meshList.forEach(VulkanMesh::cleanUp);
        ...
    }

    public void init(Window window) {
        ...
        this.meshList = new ArrayList<>();
    }

    public void loadMeshes(MeshData[] meshDataList) {
        LOGGER.debug("Loading {} meshe(s)", meshDataList.length);
        VulkanMesh[] meshes = VulkanMesh.loadMeshes(this.commandPool, this.graphQueue, meshDataList);
        LOGGER.debug("Loaded {} meshe(s)", meshes.length);
        this.meshList.addAll(Arrays.asList(meshes));
    }

    public void render(Window window, Scene scene) {
        this.swapChain.acquireNextImage();

        this.fwdRenderActivity.recordCommandBuffers(this.meshList);
        this.fwdRenderActivity.submit(this.presentQueue);

        this.swapChain.presentImage(this.graphQueue);
    }

    public void unloadMesh(String id) {
        this.meshList.removeIf(mesh -> mesh.getId().equals(id));
    }

    public void unloadMeshes() {
        this.device.waitIdle();
        this.meshList.forEach(VulkanMesh::cleanUp);
        this.meshList.clear();
    }
```

We have created a new attribute named `meshList` to hold the loaded meshes. The `loadMeshes` method just creates `VulkanMesh` instances using the data contained in the array of `MeshData`instances. If you recall, this implies creating the underlying buffers and loading the data into them. We provide method to remove all the meshes (`unloadMeshes`) or a specific one using its identifier (`unloadMesh`). 

The `render` method has been changed also. Prior to submitting the command buffers to the queue we call to a method named `recordCommandBuffers` to record the draw commands. In the previous chapter, we just cleared the screen, so the command buffers could be pre-recorded, while, in this case, we record them form each frame. Strictly speaking, we could also pre-record them, we could just do that after the meshes have been updated. However, later on, in the next chapters, we will need to render the scene based on entities which may be more dynamic than the meshes. Keeping the recorded commands in sync with scene items may not be easy. Therefore, we have chosen a simple approach which just re-records them each frame. The usage of pre-recorded command buffers in the previous chapters was just to show that this an efficient pattern usage in Vulkan.

In the `ForwardRenderActivity` we remove the pre-recording form the constructor. Also we need to create the pipeline and the shaders that will be used. 

```java
public class ForwardRenderActivity {
    ...
    private ShaderProgram fwdShaderProgram;
    private Pipeline pipeLine;
    ...

    public ForwardRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache) {
        ...
            EngineProperties engineProperties = EngineProperties.getInstance();
            if (engineProperties.isShaderRecompilation()) {
                ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
                ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
            }

            this.fwdShaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                    {
                            new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV),
                            new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV),
                    });
            Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                    this.renderPass.getVkRenderPass(), this.fwdShaderProgram, 1, new VertexBufferStructure());
            this.pipeLine = new Pipeline(pipelineCache, pipeLineCreationInfo);
            pipeLineCreationInfo.cleanUp();

            this.commandBuffers = new CommandBuffer[numImages];
            this.fences = new Fence[numImages];
            for (int i = 0; i < numImages; i++) {
                this.commandBuffers[i] = new CommandBuffer(commandPool, true, false);
                this.fences[i] = new Fence(device, true);
            }
        ...
    }
```

As it can be seen, we have created a new configuration parameter to disable the checks that trigger shader recompilation if the shader source code has been modified. We added the code to load that property, as usual, in the `EngineProperties` class.

```java
private EngineProperties() {
...
        this.shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
...
}
```

Going back to the `ForwardRenderActivity`  constructor, after the code that checks if recompilation is required, we just create a `ShaderProgram` instance with a vertex and a fragment shader modules. As it has been said, in the loop that iterates to create the command buffers, we have removed the pre-recording. The rest is the same. 

We have created a new method named `recordCommandBuffers` which reuses some of the code from the last chapter. We first retrieve the command buffer that should be used for the current swap chain image,  set the clear values, create the render pass begin information, start the recording and the render pass:

```java
    public void recordCurrentCommandBuffers(List<VulkanMesh> meshes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = this.swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            int idx = this.swapChain.getCurrentFrame();

            Fence fence = this.fences[idx];
            CommandBuffer commandBuffer = this.commandBuffers[idx];
            FrameBuffer frameBuffer = this.frameBuffers[idx];

            fence.fenceWait();
            fence.reset();

            VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
            clearValues.apply(0, v -> v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1));
            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(this.renderPass.getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            commandBuffer.beginRecording();
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);
```

The new code starts now:

```java
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeLine.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);
```

We first call to the `vkCmdBindPipeline` function. Once bound, the next commands that are recorded will be affected by this pipeline. The `VK_PIPELINE_BIND_POINT_GRAPHICS` specifies that this refers to graphics binding point. Graphic commands will be affected by this biding, but compute commands are only affected by pipelines bound using the `VK_PIPELINE_BIND_POINT_COMPUTE` binding point. Then we define the view port. The `x` and `y` values define the screen coordinates of upper left corner of the view port, which dimensions are completed by specifying its `width` and `height`. The `minDepth` and `maxDepth` values define the range of valid depth values for the view port (any depth value outside that range will be discarded). You may have noted something weird about the view port definition. The upper left corner uses a negative value for the y-axis and the height value is also negative. This is because in Vulkan the origin of coordinates is at the top left and the y axis points downwards (the opposite of OpenGL). Personally, I'm used to the OpenGL coordinates system, the shaders, the models that I use are "adapted" to that coordinate system. This is why I prefer to flip the view port to keep on using models that assume that the y -axis point upwards. You can find more details [here](https://www.saschawillems.de/blog/2019/03/29/flipping-the-vulkan-viewport/).
![Coordinates](coordinates.svg)
Another important thing to keep in mind is that the `minDepth` and `maxDepth` values shall be in the range `[0.0,1.0]` unless the extension `VK_EXT_depth_range_unrestricted` is enabled. (This should be addressed when dealing with projection matrices).

After that, we define the scissor, which dimensions are set to the size of the full screen. In this case we do not need to flip anything, the coordinates and dimensions are relative to the view port. After that we can record the rendering of the models:

```java
            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            for (VulkanMesh mesh : meshes) {
                LongBuffer vertexBuffer = stack.mallocLong(1);
                vertexBuffer.put(0, mesh.getVerticesBuffer().getBuffer());
                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, mesh.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexed(cmdHandle, mesh.getIndicesCount(), 1, 0, 0, 0);
            }

            vkCmdEndRenderPass(cmdHandle);
            commandBuffer.endRecording();
        }
    }
```

We iterate over all the meshes and start by binding their vertices buffer by calling the `vkCmdBindVertexBuffers`. The next draw calls will use that data as an input. We also bind the buffer that holds the indices by calling the `vkCmdBindIndexBuffer` and finally we record the drawing of the vertices using those indices by calling the `vkCmdDrawIndexed`. After that, we finalize the render pass and the recording.

## Shaders code

There's still a very important task to do to render anything, we need to code the shaders themselves. We will create a vertex and a fragment shaders. The source code of the vertex shader is:

```glsl
#version 450

layout(location = 0) in vec3 entityPos;

void main()
{
    gl_Position = vec4(entityPos, 1);
}
```

Our vertices just define a single attribute, at location `0`, for the positions, and we just return that.

The source code of the fragment shader is:

```glsl
#version 450

layout(location = 0) out vec4 uFragColor;

void main()
{
    uFragColor = vec4(1, 0, 0, 1);
}
```

By now, we just return a red color.

With all these changes, after many chapters, we are now able to see a nice triangle on the screen:

<img src="screen-shot.png" title="" alt="Screen Shot" data-align="center">

[Next chapter](../chapter-07/chapter-07.md)