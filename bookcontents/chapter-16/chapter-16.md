* Indirect drawing

Until this chapter, we render the models by binding their material uniforms, their textures, their vertices and indices buffers and submitting one draw command for each of the meshes it is composed. In this chapter we will start our way to a more efficient wat of rendering, we will begin the implementation of a bind-less render. This types of renders do not receive a bunch of draw commands, instead they relay on indirect drawing commands. Indirect draw commands are, in essence, draw commands that obtain the parameters required to perform the operation from a GPU buffer (instead of relaying on previous binding operations). This is a more efficient way of drawing because:

- We remove the need to perform several bind operations before drawing each mesh.
- We need just to record a single draw call.
- We can perform in-GPU operations, such as culling, that will operate over the buffer that stores the drawing parameters through compute shaders.

As you can see, the ultimate goal is to maximize the utilization of the CPU while removing potential bottlenecks that may occur at the CPU side and latencies due to CPU to GPU communications.

You can find the complete source code for this chapter [here](../../booksamples/chapter-16).

** Overview
TBD:

** Code changes review

The first thing to be done is to check if the device supports multi draw indirect. This is done in the `Device` class when setting up the required features:
```java
public class Device {
    ...
    public Device(Instance instance, PhysicalDevice physicalDevice) {
        ...
            if (!supportedFeatures.multiDrawIndirect()) {
                throw new RuntimeException("Multi draw Indirect not supported");
            }
            features.multiDrawIndirect(true);
        ...
    }
    ...
}
```

We will create a new class named `GlobalBuffers` that will hold the data and command buffers that will be used for rendering. The class defines the following buffers as attributes:
```java
public class GlobalBuffers {
    ...
    private final VulkanBuffer animJointMatricesBuffer;
    private final VulkanBuffer animWeightsBuffer;
    private final VulkanBuffer indicesBuffer;
    private final VulkanBuffer materialsBuffer;
    private final VulkanBuffer verticesBuffer;
    private VulkanBuffer animIndirectBuffer;
    private VulkanBuffer[] animInstanceDataBuffers;
    private VulkanBuffer animVerticesBuffer;
    private VulkanBuffer indirectBuffer;
    private VulkanBuffer[] instanceDataBuffers;
    ...
}
```

Although we will view how these buffers are used during data loading and rendering, here is a brief summary of the purpose of the different buffers:
- `animJointMatricesBuffer`: This buffer will hold the matrices of the joints transformations of all the animated models.
- `animWeightsBuffer`: This buffer will hold the weights of the joints transformations of all the animated models.
- `indicesBuffer`: This buffer will hold the indices of all the models to be rendered.
- `verticesBuffer`: This buffer will hold the vertex information (position, normals, etc) of all the models to be rendered. For the animated models it will contain the data of the binding pose.
- `animIndirectBuffer`: This buffer will hold the indirect drawing commands for the animated models.
- `animInstanceDataBuffers`: This array of buffers will hold instance data for each entity associated to an animated model when rendering. That is, it wil hold the model transformation matrix and and index to to the buffer that holds material data. It is an array because we will need to update them while rendering, therefore we will need separate buffers for each swap chain image.
- `animVerticesBuffer`: This buffer will hold the vertex information for animated models after animation has been applied (This is the one that will be used for rendering).
- `indirectBuffer`:This buffer will hold the indirect drawing commands for the static models.
- `instanceDataBuffers`: This array is equivalent to the `animInstanceDataBuffers` array for static models.

In the `GlobalBuffers` we initialize some of those buffers along with some other attributes:
```java
public class GlobalBuffers {
    ...
    private static final Logger LOGGER = LogManager.getLogger();
    ...
    public GlobalBuffers(Device device) {
        LOGGER.debug("Creating global buffers");
        EngineProperties engProps = EngineProperties.getInstance();
        verticesBuffer = new VulkanBuffer(device, engProps.getMaxVerticesBuffer(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indicesBuffer = new VulkanBuffer(device, engProps.getMaxIndicesBuffer(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        int maxMaterials = engProps.getMaxMaterials();
        materialsBuffer = new VulkanBuffer(device, (long) maxMaterials * GraphConstants.VEC4_SIZE * 9, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animJointMatricesBuffer = new VulkanBuffer(device, engProps.getMaxJointMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animWeightsBuffer = new VulkanBuffer(device, engProps.getMaxAnimWeightsBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        numIndirectCommands = 0;
    }
    ...
}
```
One important thing to note is that the buffers are created with the `VK_BUFFER_USAGE_STORAGE_BUFFER_BIT` flag. The reason for that is that we will be accessing vertex and indices through indirect draw calls. Also, we will be accessing buffers directly to access per instance information and materials data (we used uniforms for that previously). Therefore, we need to access unbound buffers from the shaders, so we need to use storage buffers.

We have defined new properties to configure the size of the different buffers in the `EngineProperties` class:
```java
public class EngineProperties {
    ...
    private static final int DEFAULT_JOINT_MATRICES_BUF = 2000000;
    private static final int DEFAULT_MAX_ANIM_WEIGHTS_BUF = 100000;
    private static final int DEFAULT_MAX_INDICES_BUF = 5000000;
    ...
    private static final int DEFAULT_MAX_VERTICES_BUF = 20000000;
    ...
    private int maxAnimWeightsBuffer;
    private int maxIndicesBuffer;
    private int maxJointMatricesBuffer;
    ...
    private int maxTextures;
    private int maxVerticesBuffer;    
    ...
    private EngineProperties() {
        ...
            maxTextures = maxMaterials * 3;
            maxVerticesBuffer = Integer.parseInt(props.getOrDefault("maxVerticesBuffer", DEFAULT_MAX_VERTICES_BUF).toString());
            maxIndicesBuffer = Integer.parseInt(props.getOrDefault("maxIndicesBuffer", DEFAULT_MAX_INDICES_BUF).toString());
            maxAnimWeightsBuffer = Integer.parseInt(props.getOrDefault("maxAnimWeightsBuffer", DEFAULT_MAX_ANIM_WEIGHTS_BUF).toString();
            maxJointMatricesBuffer = Integer.parseInt(props.getOrDefault("maxJointMatricesBuffer", DEFAULT_JOINT_MATRICES_BUF).toString());
        ...
    }
    ...
    public int getMaxAnimWeightsBuffer() {
        return maxAnimWeightsBuffer;
    }

    public int getMaxIndicesBuffer() {
        return maxIndicesBuffer;
    }

    public int getMaxJointMatricesBuffer() {
        return maxJointMatricesBuffer;
    }
    ...
    public int getMaxTextures() {
        return maxTextures;
    }

    public int getMaxVerticesBuffer() {
        return maxVerticesBuffer;
    }
    ...
}
```

TBD

** TBD: Changes
- AnimationComputeActivity
- GeometryAttachments
- GeometryRenderActivity
- GuiRenderActivity
- ShadowRenderActivity
- VertexBufferStructure
- ComputePipeline
- DescriptorSetLayout
- InstancedVertexBufferStructure
- VulkanAnimENtity
- VulkanMOdel
- Render
- TextureCache
- IndexedLinkedHasMap
- Scene
- Main

TODO:

Move VulkanModel to graph package in previous chapters.