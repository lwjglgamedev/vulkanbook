package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryUtil;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.model.*;

import java.io.*;
import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class ModelsCache {

    private final Map<String, VulkanModel> modelsMap;

    public ModelsCache() {
        modelsMap = new HashMap<>();
    }

    private static TransferBuffer createIndicesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream idxInput)
            throws IOException {
        int bufferSize = meshData.idxSize();
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        IntBuffer data = MemoryUtil.memIntBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int valuesToRead = meshData.idxSize() / VkUtils.INT_SIZE;
        while (valuesToRead > 0) {
            data.put(idxInput.readInt());
            valuesToRead--;
        }

        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }

    private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream vtxInput)
            throws IOException {
        int bufferSize = meshData.vtxSize();
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map(vkCtx);
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int valuesToRead = meshData.vtxSize() / VkUtils.FLOAT_SIZE;
        while (valuesToRead > 0) {
            data.put(vtxInput.readFloat());
            valuesToRead--;
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

    public void loadModels(VkCtx vkCtx, List<ModelData> models, CmdPool cmdPool, Queue queue) {
        try {
            List<VkBuffer> stagingBufferList = new ArrayList<>();

            var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmd.beginRecording();

            for (ModelData modelData : models) {
                VulkanModel vulkanModel = new VulkanModel(modelData.id());
                modelsMap.put(vulkanModel.getId(), vulkanModel);

                DataInputStream vtxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.vtxPath())));
                DataInputStream idxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.idxPath())));
                // Transform meshes loading their data into GPU buffers
                for (MeshData meshData : modelData.meshes()) {
                    TransferBuffer verticesBuffers = createVerticesBuffers(vkCtx, meshData, vtxInput);
                    TransferBuffer indicesBuffers = createIndicesBuffers(vkCtx, meshData, idxInput);
                    stagingBufferList.add(verticesBuffers.srcBuffer());
                    stagingBufferList.add(indicesBuffers.srcBuffer());
                    verticesBuffers.recordTransferCommand(cmd);
                    indicesBuffers.recordTransferCommand(cmd);

                    VulkanMesh vulkanMesh = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(),
                            indicesBuffers.dstBuffer(), meshData.idxSize() / VkUtils.INT_SIZE, meshData.materialId());
                    vulkanModel.getVulkanMeshList().add(vulkanMesh);
                }
            }

            cmd.endRecording();
            cmd.submitAndWait(vkCtx, queue);
            cmd.cleanup(vkCtx, cmdPool);

            stagingBufferList.forEach(b -> b.cleanup(vkCtx));
        } catch (Exception excp) {
            throw new RuntimeException(excp);
        }
    }
}
