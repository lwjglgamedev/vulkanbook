package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

// TODO: Test animation
public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndexedIndirectCommand.SIZEOF;
    private static final int BUFF_SIZE = 1024 * 1024 * 20;
    private static final Logger LOGGER = LogManager.getLogger();

    // Handle std430 alignment
    private static final int MATERIAL_PADDING = GraphConstants.FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = GraphConstants.VEC4_SIZE + GraphConstants.INT_LENGTH * 3 +
            GraphConstants.FLOAT_LENGTH * 2 + MATERIAL_PADDING;
    private final VulkanBuffer animIndirectBuffer;
    private final VulkanBuffer animJointMatricesBuffer;
    private final VulkanBuffer animWeightsBuffer;
    private final VulkanBuffer indicesBuffer;
    private final VulkanBuffer indirectBuffer;
    private final VulkanBuffer materialsBuffer;
    private final VulkanBuffer verticesBuffer;
    private VulkanBuffer animInstanceDataBuffer;
    // TODO: Check this
    private boolean indirectRecorded;
    // TODO: One model for image
    private VulkanBuffer instanceDataBuffer;
    private int numAnimIndrectCommands;
    private int numIndirectCommands;

    // TODO: manage size
    public GlobalBuffers(Device device) {
        LOGGER.debug("Creating global buffers");
        indirectRecorded = false;
        verticesBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indicesBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indirectBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animIndirectBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        materialsBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animJointMatricesBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        animWeightsBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        numIndirectCommands = 0;
    }

    public void cleanup() {
        LOGGER.debug("Destroying global buffers");
        verticesBuffer.cleanup();
        indicesBuffer.cleanup();
        indirectBuffer.cleanup();
        animIndirectBuffer.cleanup();
        materialsBuffer.cleanup();
        animJointMatricesBuffer.cleanup();
        animWeightsBuffer.cleanup();
        if (instanceDataBuffer != null) {
            instanceDataBuffer.cleanup();
        }
        if (animInstanceDataBuffer != null) {
            animInstanceDataBuffer.cleanup();
        }
    }

    public VulkanBuffer getIndicesBuffer() {
        return indicesBuffer;
    }

    public VulkanBuffer getIndirectBuffer() {
        return indirectBuffer;
    }

    public VulkanBuffer getInstanceDataBuffer() {
        return instanceDataBuffer;
    }

    public VulkanBuffer getMaterialsBuffer() {
        return materialsBuffer;
    }

    public int getNumAnimIndrectCommands() {
        return numAnimIndrectCommands;
    }

    public int getNumIndirectCommands() {
        return numIndirectCommands;
    }

    public VulkanBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    public boolean isIndirectRecorded() {
        return indirectRecorded;
    }

    private void loadAnimEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool, Queue
            queue) {
        numAnimIndrectCommands = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

            cmd.beginRecording();

            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty()) {
                    continue;
                }
                for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    entities.stream().filter(e -> e.hasAnimation()).forEach(e -> {
                        VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.callocStack(stack);
                        indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                        indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / GraphConstants.INT_LENGTH);
                        indexedIndirectCommand.instanceCount(1);
                        indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                        indexedIndirectCommand.firstInstance(0);
                        indexedIndirectCommandList.add(indexedIndirectCommand);
                    });
                }
            }
            numAnimIndrectCommands = indexedIndirectCommandList.size();
            StgBuffer indirectStgBuffer = new StgBuffer(device, IND_COMMAND_STRIDE * numAnimIndrectCommands);
            ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
            VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

            indexedIndirectCommandList.forEach(i -> indCommandBuffer.put(i));

            animInstanceDataBuffer = new VulkanBuffer(device, numAnimIndrectCommands * (GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH),
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            indirectStgBuffer.recordTransferCommand(cmd, animIndirectBuffer);

            cmd.endRecording();
            cmd.submitAndWait(device, queue);
            cmd.cleanup();
            indirectStgBuffer.cleanup();
        }
    }

    private void loadAnimationData(ModelData modelData, StgBuffer animJointMatricesStgBuffer) {
        List<ModelData.Animation> animationsList = modelData.getAnimationsList();
        boolean hasAnimation = animationsList != null && !animationsList.isEmpty();
        if (!hasAnimation) {
            return;
        }
        ByteBuffer dataBuffer = animJointMatricesStgBuffer.getDataBuffer();
        for (ModelData.Animation animation : animationsList) {
            // TODO: record number of frames
            List<ModelData.AnimatedFrame> frameList = animation.frames();
            for (ModelData.AnimatedFrame frame : frameList) {
                Matrix4f[] matrices = frame.jointMatrices();
                int numMatrices = matrices.length;
                for (int i = 0; i < numMatrices; i++) {
                    matrices[i].get(dataBuffer);
                    dataBuffer.position(dataBuffer.position() + GraphConstants.MAT4X4_SIZE);
                }
            }
        }
    }

    public void loadEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool, Queue
            queue) {
        loadStaticEntities(vulkanModelList, scene, commandPool, queue);
        loadAnimEntities(vulkanModelList, scene, commandPool, queue);
    }

    public void loadInstanceData(Scene scene, List<VulkanModel> vulkanModels) {
        long mappedMemory = instanceDataBuffer.map();
        ByteBuffer dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) instanceDataBuffer.getRequestedSize());
        instanceDataBuffer.map();
        int pos = 0;
        for (VulkanModel vulkanModel : vulkanModels) {
            List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
            if (entities.isEmpty()) {
                continue;
            }
            for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                for (Entity entity : entities) {
                    entity.getModelMatrix().get(pos, dataBuffer);
                    pos += GraphConstants.MAT4X4_SIZE;
                    dataBuffer.putInt(pos, vulkanMesh.globalMaterialIdx());
                    pos += GraphConstants.INT_LENGTH;
                }
            }
        }
        instanceDataBuffer.unMap();
    }

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

    private void loadMeshes(StgBuffer verticesStgBuffer, StgBuffer indicesStgBuffer, StgBuffer animWeightsStgBuffer,
                            ModelData modelData, VulkanModel vulkanModel, List<VulkanModel.VulkanMaterial> vulkanMaterialList) {
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

            ByteBuffer verticesData = verticesStgBuffer.getDataBuffer();
            ByteBuffer indicesData = indicesStgBuffer.getDataBuffer();

            int localMaterialIdx = meshData.materialIdx();
            int globalMaterialIdx = 0;
            if (localMaterialIdx >= 0 && localMaterialIdx < vulkanMaterialList.size()) {
                globalMaterialIdx = vulkanMaterialList.get(localMaterialIdx).globalMaterialIdx();
            }
            vulkanModel.addVulkanMesh(new VulkanModel.VulkanMesh(verticesSize, indices.length,
                    verticesData.position(), indicesData.position(), globalMaterialIdx));

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

            Arrays.stream(indices).forEach(i -> indicesData.putInt(i));

            loadWeightsBuffer(modelData, meshData, animWeightsStgBuffer, meshCount);
            meshCount++;
        }
    }

    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool
            commandPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

        StgBuffer verticesStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer indicesStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer materialsStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer animJointMatricesStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer animWeightsStgBuffer = new StgBuffer(device, BUFF_SIZE);

        cmd.beginRecording();

        // Load a default material
        List<ModelData.Material> defaultMaterialList = Arrays.asList(new ModelData.Material());
        loadMaterials(device, textureCache, materialsStgBuffer, defaultMaterialList, textureList);

        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            List<VulkanModel.VulkanMaterial> vulkanMaterialList = loadMaterials(device, textureCache, materialsStgBuffer,
                    modelData.getMaterialList(), textureList);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, vulkanMaterialList);
            loadAnimationData(modelData, animJointMatricesStgBuffer);
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

    private void loadStaticEntities(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool, Queue
            queue) {
        numIndirectCommands = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

            cmd.beginRecording();

            List<VkDrawIndexedIndirectCommand> indexedIndirectCommandList = new ArrayList<>();
            int numInstances = 0;
            int firstInstance = 0;
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getModelId());
                if (entities.isEmpty()) {
                    continue;
                }
                for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.callocStack(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / GraphConstants.INT_LENGTH);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(firstInstance);
                    indexedIndirectCommandList.add(indexedIndirectCommand);

                    numIndirectCommands++;
                    firstInstance++;
                    numInstances = numInstances + entities.size();
                }
            }
            StgBuffer indirectStgBuffer = new StgBuffer(device, IND_COMMAND_STRIDE * numIndirectCommands);
            ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
            VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

            indexedIndirectCommandList.forEach(i -> indCommandBuffer.put(i));

            instanceDataBuffer = new VulkanBuffer(device, numInstances * (GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH),
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

            indirectStgBuffer.recordTransferCommand(cmd, indirectBuffer);

            cmd.endRecording();
            cmd.submitAndWait(device, queue);
            cmd.cleanup();
            indirectStgBuffer.cleanup();
        }

        indirectRecorded = true;
    }

    private void loadWeightsBuffer(ModelData modelData, ModelData.MeshData meshData, StgBuffer animWeightsBuffer, int meshCount) {
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

    private static class StgBuffer {
        private final ByteBuffer dataBuffer;
        private final VulkanBuffer stgBuffer;

        public StgBuffer(Device device, int size) {
            stgBuffer = new VulkanBuffer(device, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            long mappedMemory = stgBuffer.map();
            dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgBuffer.getRequestedSize());
        }

        public void cleanup() {
            stgBuffer.unMap();
            stgBuffer.cleanup();
        }

        public ByteBuffer getDataBuffer() {
            return dataBuffer;
        }

        private void recordTransferCommand(CommandBuffer cmd, VulkanBuffer dstBuffer) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack)
                        .srcOffset(0).dstOffset(0).size(stgBuffer.getRequestedSize());
                vkCmdCopyBuffer(cmd.getVkCommandBuffer(), stgBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
    }
}
