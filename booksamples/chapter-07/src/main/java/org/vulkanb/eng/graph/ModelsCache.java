package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryUtil;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.model.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

public class ModelsCache {

    private final Map<String, VulkanModel> modelsMap;

    public ModelsCache() {
        modelsMap = new HashMap<>();
    }

    private static TransferBuffer createIndicesBuffers(VkCtx vkCtx, MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * VkUtils.INT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        IntBuffer data = MemoryUtil.memIntBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        data.put(indices);
        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }

    private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData) {
        float[] positions = meshData.positions();
        float[] textCoords = meshData.textCoords();
        if (textCoords == null || textCoords.length == 0) {
            textCoords = new float[(positions.length / 3) * 2];
        }
        int numElements = positions.length + textCoords.length;
        int bufferSize = numElements * VkUtils.FLOAT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = positions.length / 3;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            int startTextCoord = row * 2;
            data.put(positions[startPos]);
            data.put(positions[startPos + 1]);
            data.put(positions[startPos + 2]);
            data.put(textCoords[startTextCoord]);
            data.put(textCoords[startTextCoord + 1]);
        }

        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }

    public void cleanup(VkCtx vkCtx) {
        modelsMap.forEach((k, t) -> t.cleanup(vkCtx));
        modelsMap.clear();
    }

    public VulkanModel getModel(String modelName) {
        return modelsMap.get(modelName);
    }

    public Map<String, VulkanModel> getModelsMap() {
        return modelsMap;
    }

    public void loadModels(VkCtx vkCtx, List<ModelData> models, CmdPool cmdPool, Queue queue) {
        List<VkBuffer> stagingBufferList = new ArrayList<>();

        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmd.beginRecording();

        for (ModelData modelData : models) {
            VulkanModel vulkanModel = new VulkanModel(modelData.id());
            modelsMap.put(vulkanModel.getId(), vulkanModel);

            // Transform meshes loading their data into GPU buffers
            for (MeshData meshData : modelData.meshes()) {
                TransferBuffer verticesBuffers = createVerticesBuffers(vkCtx, meshData);
                TransferBuffer indicesBuffers = createIndicesBuffers(vkCtx, meshData);
                stagingBufferList.add(verticesBuffers.srcBuffer());
                stagingBufferList.add(indicesBuffers.srcBuffer());
                verticesBuffers.recordTransferCommand(cmd);
                indicesBuffers.recordTransferCommand(cmd);

                VulkanMesh vulkanMesh = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(),
                        indicesBuffers.dstBuffer(), meshData.indices().length);
                vulkanModel.getVulkanMeshList().add(vulkanMesh);
            }
        }

        cmd.endRecording();
        cmd.submitAndWait(vkCtx, queue);
        cmd.cleanup(vkCtx, cmdPool);

        stagingBufferList.forEach(b -> b.cleanup(vkCtx));
    }
}
