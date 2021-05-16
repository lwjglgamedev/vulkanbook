package org.vulkanb.eng.graph;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class GlobalBuffers {

    private static final int BUFF_SIZE = 1024 * 1024 * 20;
    // Handle std430 alignment
    private static final int MATERIAL_PADDING = GraphConstants.FLOAT_LENGTH * 3;
    private static final int MATERIAL_SIZE = GraphConstants.VEC4_SIZE + GraphConstants.INT_LENGTH * 3 +
            GraphConstants.FLOAT_LENGTH * 2 + MATERIAL_PADDING;

    private final VulkanBuffer indicesBuffer;
    private final VulkanBuffer indirectBuffer;
    private final VulkanBuffer materialsBuffer;
    private final VulkanBuffer verticesBuffer;
    private int drawCount;
    // TODO: Check this
    private boolean indirectRecorded;

    public GlobalBuffers(Device device) {
        indirectRecorded = false;
        verticesBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indicesBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        indirectBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
        materialsBuffer = new VulkanBuffer(device, BUFF_SIZE, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        drawCount = 0;
    }

    public void cleanup() {
        verticesBuffer.cleanup();
        indicesBuffer.cleanup();
        indirectBuffer.cleanup();
        materialsBuffer.cleanup();
    }

    public int getDrawCount() {
        return drawCount;
    }

    public VulkanBuffer getIndicesBuffer() {
        return indicesBuffer;
    }

    public VulkanBuffer getIndirectBuffer() {
        return indirectBuffer;
    }

    public VulkanBuffer getMaterialsBuffer() {
        return materialsBuffer;
    }

    public VulkanBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    public boolean isIndirectRecorded() {
        return indirectRecorded;
    }

    public void loadGameItems(List<VulkanModel> vulkanModelList, Scene scene, CommandPool commandPool, Queue queue) {
        drawCount = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

            cmd.beginRecording();

            StgBuffer indirectStgBuffer = new StgBuffer(device, BUFF_SIZE);
            ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
            VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

            for (VulkanModel vulkanModel : vulkanModelList) {
                String modelId = vulkanModel.getModelId();
                List<Entity> entities = scene.getEntitiesByModelId(modelId);
                if (entities.isEmpty()) {
                    continue;
                }
                for (VulkanModel.VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.callocStack(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / GraphConstants.INT_LENGTH);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VertexBufferStructure.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(vulkanMesh.globalMaterialIdx());

                    indCommandBuffer.put(indexedIndirectCommand);
                    drawCount++;
                }
            }

            indirectStgBuffer.recordTransferCommand(cmd, indirectBuffer);

            cmd.endRecording();
            cmd.submitAndWait(device, queue);
            cmd.cleanup();
            indirectStgBuffer.cleanup();
        }

        indirectRecorded = true;
    }

    private List<VulkanModel.VulkanMaterial> loadMaterials(Device device, TextureCache textureCache, StgBuffer materialsStgBuffer,
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

    private void loadMeshes(StgBuffer verticesStgBuffer, StgBuffer indicesStgBuffer, List<ModelData.MeshData> meshDataList,
                            VulkanModel vulkanModel, List<VulkanModel.VulkanMaterial> vulkanMaterialList) {
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
        }
    }

    public List<VulkanModel> loadModels(List<ModelData> modelDataList, TextureCache textureCache, CommandPool commandPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        Device device = commandPool.getDevice();
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);

        StgBuffer verticesStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer indicesStgBuffer = new StgBuffer(device, BUFF_SIZE);
        StgBuffer materialsStgBuffer = new StgBuffer(device, BUFF_SIZE);

        cmd.beginRecording();

        // Load a default material
        List<ModelData.Material> defaultMaterialList = Arrays.asList(new ModelData.Material());
        loadMaterials(device, textureCache, materialsStgBuffer, defaultMaterialList, textureList);

        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.getModelId());
            vulkanModelList.add(vulkanModel);

            List<VulkanModel.VulkanMaterial> vulkanMaterialList = loadMaterials(device, textureCache, materialsStgBuffer,
                    modelData.getMaterialList(), textureList);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, modelData.getMeshDataList(), vulkanModel, vulkanMaterialList);
        }

        materialsStgBuffer.recordTransferCommand(cmd, materialsBuffer);
        verticesStgBuffer.recordTransferCommand(cmd, verticesBuffer);
        indicesStgBuffer.recordTransferCommand(cmd, indicesBuffer);
        textureList.forEach(t -> t.recordTextureTransition(cmd));
        cmd.endRecording();

        cmd.submitAndWait(device, queue);
        cmd.cleanup();

        verticesStgBuffer.cleanup();
        indicesStgBuffer.cleanup();
        materialsStgBuffer.cleanup();
        textureList.forEach(Texture::cleanupStgBuffer);

        return vulkanModelList;
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
