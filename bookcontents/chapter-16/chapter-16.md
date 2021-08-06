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

Back to the `GlobalBuffers` class, the next method is the `cleanup()` method, which frees all the resources that were allocated during the initialization.

```java
public class GlobalBuffers {
    ...
    public void cleanup() {
        LOGGER.debug("Destroying global buffers");
        verticesBuffer.cleanup();
        indicesBuffer.cleanup();
        if (indirectBuffer != null) {
            indirectBuffer.cleanup();
        }
        if (animVerticesBuffer != null) {
            animVerticesBuffer.cleanup();
        }
        if (animIndirectBuffer != null) {
            animIndirectBuffer.cleanup();
        }
        materialsBuffer.cleanup();
        animJointMatricesBuffer.cleanup();
        animWeightsBuffer.cleanup();
        if (instanceDataBuffers != null) {
            Arrays.stream(instanceDataBuffers).forEach(VulkanBuffer::cleanup);
        }
        if (animInstanceDataBuffers != null) {
            Arrays.stream(animInstanceDataBuffers).forEach(VulkanBuffer::cleanup);
        }
    }
    ...
}
```

We will continue now analyzing the `GlobalBuffers` class by starting with the `loadModels` method, which starts like this:
```java
public class GlobalBuffers {
    ...
    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool
            commandPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

        StgBuffer verticesStgBuffer = new StgBuffer(device, verticesBuffer.getRequestedSize());
        StgBuffer indicesStgBuffer = new StgBuffer(device, indicesBuffer.getRequestedSize());
        StgBuffer materialsStgBuffer = new StgBuffer(device, materialsBuffer.getRequestedSize());
        StgBuffer animJointMatricesStgBuffer = new StgBuffer(device, animJointMatricesBuffer.getRequestedSize());
        StgBuffer animWeightsStgBuffer = new StgBuffer(device, animWeightsBuffer.getRequestedSize());

        cmd.beginRecording();

        // Load a default material
        List<ModelData.Material> defaultMaterialList = Collections.singletonList(new ModelData.Material());
        loadMaterials(device, textureCache, materialsStgBuffer, defaultMaterialList, textureList);
        ...
    }
    ...
}
```

The `loadModels` method starts by creating several `StgBuffer` instances fpr the vertex data, indices, materials, etc. along with a command buffer to record the transitions. It also creates a default material which will be used for models which do not define any. One important thing to remark is that we are using a single staging buffer type for all the models (a single one for the vertices, for the indices, etc.). The `loadModels` method continues as follows:
```java
public class GlobalBuffers {
    ...
    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool
            commandPool, Queue queue) {
        ...
        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            List<VulkanModel.VulkanMaterial> vulkanMaterialList = loadMaterials(device, textureCache, materialsStgBuffer,
                    modelData.getMaterialList(), textureList);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, vulkanMaterialList);
            loadAnimationData(modelData, vulkanModel, animJointMatricesStgBuffer);
        }

        // We need to ensure that at least we have one texture
        if (textureList.isEmpty()) {
            EngineProperties engineProperties = EngineProperties.getInstance();
            Texture defaultTexture = textureCache.createTexture(device, engineProperties.getDefaultTexturePath(),
                    VK_FORMAT_R8G8B8A8_SRGB);
            textureList.add(defaultTexture);
        }

        materialsStgBuffer.recordTransferCommand(cmd, materialsBuffer);
        verticesStgBuffer.recordTransferCommand(cmd, verticesBuffer);
        indicesStgBuffer.recordTransferCommand(cmd, indicesBuffer);
        animJointMatricesStgBuffer.recordTransferCommand(cmd, animJointMatricesBuffer);
        animWeightsStgBuffer.recordTransferCommand(cmd, animWeightsBuffer);
        textureList.forEach(t -> t.recordTextureTransition(cmd));
        cmd.endRecording();

        cmd.submitAndWait(device, queue);
        cmd.cleanup();

        verticesStgBuffer.cleanup();
        indicesStgBuffer.cleanup();
        materialsStgBuffer.cleanup();
        animJointMatricesStgBuffer.cleanup();
        animWeightsStgBuffer.cleanup();
        textureList.forEach(Texture::cleanupStgBuffer);

        return vulkanModelList;
    }
    ...
}
```

As it can be seen, we iterate over the meshes that are defined for each model, invoking the `loadMeshes` to properly upload the data to the staging buffers. We also call the `loadAnimationData` method to load the animation data for each model (if present). After that, we check if there are mp textures loaded to load a default one (which is required for simplifying the render). Finally, we record the transfer commands from CPU accessible buffers to GPU only buffers and cleanup the staging buffers.

The `StgBuffer` class, a `GlobalBuffers` inner class, allows us to use a temporary CPU accessible to a GPU only buffer:
```java
public class GlobalBuffers {
    ...
    private static class StgBuffer {
        private final ByteBuffer dataBuffer;
        private final VulkanBuffer stgVulkanBuffer;

        public StgBuffer(Device device, long size) {
            stgVulkanBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            long mappedMemory = stgVulkanBuffer.map();
            dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgVulkanBuffer.getRequestedSize());
        }

        public void cleanup() {
            stgVulkanBuffer.unMap();
            stgVulkanBuffer.cleanup();
        }

        public ByteBuffer getDataBuffer() {
            return dataBuffer;
        }

        private void recordTransferCommand(CommandBuffer cmd, VulkanBuffer dstBuffer) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack)
                        .srcOffset(0).dstOffset(0).size(stgVulkanBuffer.getRequestedSize());
                vkCmdCopyBuffer(cmd.getVkCommandBuffer(), stgVulkanBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
    }
    ...
}
```

While iterating over the models, for each of them we create a `VulkanModel` instance. This class has been completely rewritten since we do not have now independent buffers per mesh:
```java
public class VulkanModel {

    private final String modelId;
    private final List<VulkanAnimationData> vulkanAnimationDataList;
    private final List<VulkanMesh> vulkanMeshList;

    public VulkanModel(String modelId) {
        this.modelId = modelId;
        vulkanMeshList = new ArrayList<>();
        vulkanAnimationDataList = new ArrayList<>();
    }

    public void addVulkanAnimationData(VulkanAnimationData vulkanAnimationData) {
        vulkanAnimationDataList.add(vulkanAnimationData);
    }

    public void addVulkanMesh(VulkanMesh vulkanMesh) {
        vulkanMeshList.add(vulkanMesh);
    }

    public String getModelId() {
        return modelId;
    }

    public List<VulkanAnimationData> getVulkanAnimationDataList() {
        return vulkanAnimationDataList;
    }

    public List<VulkanMesh> getVulkanMeshList() {
        return vulkanMeshList;
    }

    public boolean hasAnimations() {
        return !vulkanAnimationDataList.isEmpty();
    }
    ...
    public static record VulkanMaterial(int globalMaterialIdx) {
    }
    ...
}
```

The `VulkanModel` class just stores the model identifier and two lists. The `vulkanMeshList` contains information about the different meshes that define the model, defined by the `VulkanModel.VulkanMesh` class. The `vulkanAnimationDataList` contains information about the animations that are defined for the model, defined by the `VulkanAnimationData`. Let's review the `VulkanModel.VulkanMesh` definition:
```java
public class VulkanModel {
    ...
    public static record VulkanMesh(int verticesSize, int numIndices, int verticesOffset, int indicesOffset,
                                    int globalMaterialIdx, int weightsOffset) {
    }
    ...
}
```

After loading the mesh data into the global buffer we need to record the size of the data for the vertices, the number indices, the offset of the vertices data and  indices data in their respective global buffers. We meed also to store a reference to the material which this mesh refers to and and offset to the wights buffer (for animated models).

The `VulkanAnimationData`. basically contains a list of offsets to a buffer which stores the joint matrices offset to be used for each frame of an animation. It is defined like this:
```java
public class VulkanModel {
    ...
    public static class VulkanAnimationData {
        private List<VulkanAnimationFrame> vulkanAnimationFrameList;

        public VulkanAnimationData() {
            vulkanAnimationFrameList = new ArrayList<>();
        }

        public void addVulkanAnimationFrame(VulkanAnimationFrame vulkanAnimationFrame) {
            vulkanAnimationFrameList.add(vulkanAnimationFrame);
        }

        public List<VulkanAnimationFrame> getVulkanAnimationFrameList() {
            return vulkanAnimationFrameList;
        }
    }

    public static record VulkanAnimationFrame(int jointMatricesOffset) {

    }
    ...
}
```

We can now go back to the `GlobalBuffers` class and examine the `loadMaterials` method:
```java
public class GlobalBuffers {
    ...
    // Handle std430 alignment
    private static final int MATERIAL_PADDING = GraphConstants.FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = GraphConstants.VEC4_SIZE + GraphConstants.INT_LENGTH * 3 +
            GraphConstants.FLOAT_LENGTH * 2 + MATERIAL_PADDING;
    ...
    private List<VulkanModel.VulkanMaterial> loadMaterials(Device device, TextureCache textureCache, StgBuffer
            materialsStgBuffer,
                                                           List<ModelData.Material> materialList, List<Texture> textureList) {
        List<VulkanModel.VulkanMaterial> vulkanMaterialList = new ArrayList<>();
        for (ModelData.Material material : materialList) {
            ByteBuffer dataBuffer = materialsStgBuffer.getDataBuffer();

            Texture texture = textureCache.createTexture(device, material.texturePath(), VK_FORMAT_R8G8B8A8_SRGB);
            if (texture != null) {
                textureList.add(texture);
            }
            int textureIdx = textureCache.getPosition(material.texturePath());

            texture = textureCache.createTexture(device, material.normalMapPath(), VK_FORMAT_R8G8B8A8_UNORM);
            if (texture != null) {
                textureList.add(texture);
            }
            int normalMapIdx = textureCache.getPosition(material.normalMapPath());

            texture = textureCache.createTexture(device, material.metalRoughMap(), VK_FORMAT_R8G8B8A8_SRGB);
            if (texture != null) {
                textureList.add(texture);
            }
            int metalRoughMapIdx = textureCache.getPosition(material.metalRoughMap());

            vulkanMaterialList.add(new VulkanModel.VulkanMaterial(dataBuffer.position() / MATERIAL_SIZE));
            material.diffuseColor().get(dataBuffer);
            dataBuffer.position(dataBuffer.position() + GraphConstants.VEC4_SIZE);
            dataBuffer.putInt(textureIdx);
            dataBuffer.putInt(normalMapIdx);
            dataBuffer.putInt(metalRoughMapIdx);
            dataBuffer.putFloat(material.roughnessFactor());
            dataBuffer.putFloat(material.metallicFactor());
            // Padding due to std430 alignment
            dataBuffer.putFloat(0.0f);
            dataBuffer.putFloat(0.0f);
            dataBuffer.putFloat(0.0f);
        }

        return vulkanMaterialList;
    }
    ...
}
```
The `loadMaterials` will look familiar to you, the only difference is that we will be storing all that information into a single buffer In order to properly access the data for each material in t he shaders we will assign to each material an unique identifier, which in essence will be used as way to calculate the offset required in that global materials buffer. One important thing to highlight is that we are applying a padding at the end of each block of material data. The reason for that is that we will using `std430` layout when accessing the material data in the shaders. This means that the data will be aligned to the nearest 4 bytes.

The `loadMeshes` is defined like this:
```java
public class GlobalBuffers {
    ...
    private void loadMeshes(StgBuffer verticesStgBuffer, StgBuffer indicesStgBuffer, StgBuffer animWeightsStgBuffer,
                            ModelData modelData, VulkanModel vulkanModel, List<VulkanModel.VulkanMaterial> vulkanMaterialList) {
        ByteBuffer verticesData = verticesStgBuffer.getDataBuffer();
        ByteBuffer indicesData = indicesStgBuffer.getDataBuffer();
        List<ModelData.MeshData> meshDataList = modelData.getMeshDataList();
        int meshCount = 0;
        for (ModelData.MeshData meshData : meshDataList) {
            float[] positions = meshData.positions();
            float[] normals = meshData.normals();
            float[] tangents = meshData.tangents();
            float[] biTangents = meshData.biTangents();
            float[] textCoords = meshData.textCoords();
            if (textCoords == null || textCoords.length == 0) {
                textCoords = new float[(positions.length / 3) * 2];
            }
            int[] indices = meshData.indices();

            int numElements = positions.length + normals.length + tangents.length + biTangents.length + textCoords.length;
            int verticesSize = numElements * GraphConstants.FLOAT_LENGTH;

            int localMaterialIdx = meshData.materialIdx();
            int globalMaterialIdx = 0;
            if (localMaterialIdx >= 0 && localMaterialIdx < vulkanMaterialList.size()) {
                globalMaterialIdx = vulkanMaterialList.get(localMaterialIdx).globalMaterialIdx();
            }
            vulkanModel.addVulkanMesh(new VulkanModel.VulkanMesh(verticesSize, indices.length,
                    verticesData.position(), indicesData.position(), globalMaterialIdx, animWeightsStgBuffer.getDataBuffer().position()));

            int rows = positions.length / 3;
            for (int row = 0; row < rows; row++) {
                int startPos = row * 3;
                int startTextCoord = row * 2;
                verticesData.putFloat(positions[startPos]);
                verticesData.putFloat(positions[startPos + 1]);
                verticesData.putFloat(positions[startPos + 2]);
                verticesData.putFloat(normals[startPos]);
                verticesData.putFloat(normals[startPos + 1]);
                verticesData.putFloat(normals[startPos + 2]);
                verticesData.putFloat(tangents[startPos]);
                verticesData.putFloat(tangents[startPos + 1]);
                verticesData.putFloat(tangents[startPos + 2]);
                verticesData.putFloat(biTangents[startPos]);
                verticesData.putFloat(biTangents[startPos + 1]);
                verticesData.putFloat(biTangents[startPos + 2]);
                verticesData.putFloat(textCoords[startTextCoord]);
                verticesData.putFloat(textCoords[startTextCoord + 1]);
            }

            Arrays.stream(indices).forEach(indicesData::putInt);

            loadWeightsBuffer(modelData, animWeightsStgBuffer, meshCount);
            meshCount++;
        }
    }
    ...
}
```
This method is quite similar to the one we saw in previous chapters. The difference is that we are now copying the date to a single vertices buffer, a single indices buffer and another one for the vertex weights for all the models. As you can see, we store that information of each of the meshes in an instance of the `VulkanMesh` class.

While we are copying the vertex data to the buffer, we also check if the model has animations an copy the weights used for the animation to a common weights buffer. This is done in the `loadWeightsBuffer` method.
```java
public class GlobalBuffers {
    ...
    private void loadWeightsBuffer(ModelData modelData, StgBuffer animWeightsBuffer, int meshCount) {
        List<ModelData.AnimMeshData> animMeshDataList = modelData.getAnimMeshDataList();
        if (animMeshDataList == null || animMeshDataList.isEmpty()) {
            return;
        }

        ModelData.AnimMeshData animMeshData = animMeshDataList.get(meshCount);
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();

        ByteBuffer dataBuffer = animWeightsBuffer.getDataBuffer();

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            dataBuffer.putFloat(weights[startPos]);
            dataBuffer.putFloat(weights[startPos + 1]);
            dataBuffer.putFloat(weights[startPos + 2]);
            dataBuffer.putFloat(weights[startPos + 3]);
            dataBuffer.putFloat(boneIds[startPos]);
            dataBuffer.putFloat(boneIds[startPos + 1]);
            dataBuffer.putFloat(boneIds[startPos + 2]);
            dataBuffer.putFloat(boneIds[startPos + 3]);
        }
    }
    ...
}
```

Finally, to complete the model loading we need to load the data for each animation frame if the model is animated. That data is basically the transformation matrices for each frame. This is done in the `loadAnimationData` method, which is called in the `loadModels` method while traversing the models.
```java
public class GlobalBuffers {
    ...
    private void loadAnimationData(ModelData modelData, VulkanModel vulkanModel, StgBuffer animJointMatricesStgBuffer) {
        List<ModelData.Animation> animationsList = modelData.getAnimationsList();
        if (!modelData.hasAnimations()) {
            return;
        }
        ByteBuffer dataBuffer = animJointMatricesStgBuffer.getDataBuffer();
        for (ModelData.Animation animation : animationsList) {
            VulkanModel.VulkanAnimationData vulkanAnimationData = new VulkanModel.VulkanAnimationData();
            vulkanModel.addVulkanAnimationData(vulkanAnimationData);
            List<ModelData.AnimatedFrame> frameList = animation.frames();
            for (ModelData.AnimatedFrame frame : frameList) {
                vulkanAnimationData.addVulkanAnimationFrame(new VulkanModel.VulkanAnimationFrame(dataBuffer.position()));
                Matrix4f[] matrices = frame.jointMatrices();
                for (Matrix4f matrix : matrices) {
                    matrix.get(dataBuffer);
                    dataBuffer.position(dataBuffer.position() + GraphConstants.MAT4X4_SIZE);
                }
            }
        }
    }
    ...
}
```

With all the code presented above we have now all the meshes data in GPU accessible buffers, ready to be used by our shaders. Now it comes the most important part of indirect drawing, we need to create a buffer that will contain the indirect drawing commands that will allow us to render scene. The `GlobalBuffers` class has a method that performs all the necessary steps to create the indirect drawing commands buffer upon a list of entities:
```java
public class GlobalBuffers {
    ...
    public void loadEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool,
                             Queue queue, int numSwapChainImages) {
        loadStaticEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
        loadAnimEntities(vulkanModelList, scene, commandPool, queue, numSwapChainImages);
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
- VulkanAnimEntity
- Render
- TextureCache
- IndexedLinkedHasMap
- Scene
- Main