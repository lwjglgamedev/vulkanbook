package org.vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.model.*;

import java.io.*;
import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

public class ModelsCache {

    private final Map<String, VulkanModel> modelsMap;

    public ModelsCache() {
        modelsMap = new HashMap<>();
    }

    private static TransferBuffer createIndicesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream idxInput)
            throws IOException {
        int bufferSize = meshData.idxSize();
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

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

    private static TransferBuffer createJointMatricesBuffers(VkCtx vkCtx, AnimatedFrame frame) {
        Matrix4f[] matrices = frame.jointMatrices();
        int numMatrices = matrices.length;
        int bufferSize = numMatrices * VkUtils.MAT4X4_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

        long mappedMemory = srcBuffer.map(vkCtx);
        ByteBuffer matrixBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        for (int i = 0; i < numMatrices; i++) {
            matrices[i].get(i * VkUtils.MAT4X4_SIZE, matrixBuffer);
        }
        srcBuffer.unMap(vkCtx);

        return new TransferBuffer(srcBuffer, dstBuffer);
    }

    private static TransferBuffer createVerticesBuffers(VkCtx vkCtx, MeshData meshData, DataInputStream vtxInput)
            throws IOException {
        int bufferSize = meshData.vtxSize();
        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

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

    private static TransferBuffer createWeightsBuffers(VkCtx vkCtx, AnimMeshData animMeshData) {
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();
        int bufferSize = weights.length * VkUtils.FLOAT_SIZE + boneIds.length * VkUtils.INT_SIZE;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        var dstBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

        long mappedMemory = srcBuffer.map(vkCtx);
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            data.put(weights[startPos]);
            data.put(weights[startPos + 1]);
            data.put(weights[startPos + 2]);
            data.put(weights[startPos + 3]);
            data.put(boneIds[startPos]);
            data.put(boneIds[startPos + 1]);
            data.put(boneIds[startPos + 2]);
            data.put(boneIds[startPos + 3]);
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
        try {
            List<VkBuffer> stagingBufferList = new ArrayList<>();

            var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
            cmd.beginRecording();

            for (ModelData modelData : models) {
                VulkanModel vulkanModel = new VulkanModel(modelData.id());
                modelsMap.put(vulkanModel.getId(), vulkanModel);

                List<Animation> animationsList = modelData.animationsList();
                boolean hasAnimation = animationsList != null && !animationsList.isEmpty();
                if (hasAnimation) {
                    for (Animation animation : animationsList) {
                        List<VkBuffer> vulkanFrameBufferList = new ArrayList<>();
                        VulkanAnimation vulkanAnimation = new VulkanAnimation(animation.name(), vulkanFrameBufferList);
                        vulkanModel.addVulkanAnimation(vulkanAnimation);
                        List<AnimatedFrame> frameList = animation.frames();
                        for (AnimatedFrame frame : frameList) {
                            TransferBuffer jointMatricesBuffers = createJointMatricesBuffers(vkCtx, frame);
                            stagingBufferList.add(jointMatricesBuffers.srcBuffer());
                            jointMatricesBuffers.recordTransferCommand(cmd);
                            vulkanFrameBufferList.add(jointMatricesBuffers.dstBuffer());
                        }
                    }
                }

                DataInputStream vtxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.vtxPath())));
                DataInputStream idxInput = new DataInputStream(new BufferedInputStream(new FileInputStream(modelData.idxPath())));
                // Transform meshes loading their data into GPU buffers
                int meshCount = 0;
                for (MeshData meshData : modelData.meshes()) {
                    TransferBuffer verticesBuffers = createVerticesBuffers(vkCtx, meshData, vtxInput);
                    TransferBuffer indicesBuffers = createIndicesBuffers(vkCtx, meshData, idxInput);
                    stagingBufferList.add(verticesBuffers.srcBuffer());
                    stagingBufferList.add(indicesBuffers.srcBuffer());
                    verticesBuffers.recordTransferCommand(cmd);
                    indicesBuffers.recordTransferCommand(cmd);

                    TransferBuffer weightsBuffers = null;
                    List<AnimMeshData> animMeshDataList = modelData.animMeshDataList();
                    if (animMeshDataList != null && !animMeshDataList.isEmpty()) {
                        weightsBuffers = createWeightsBuffers(vkCtx, animMeshDataList.get(meshCount));
                        stagingBufferList.add(weightsBuffers.srcBuffer());
                        weightsBuffers.recordTransferCommand(cmd);
                    }

                    VulkanMesh vulkanMesh = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(),
                            indicesBuffers.dstBuffer(), weightsBuffers != null ? weightsBuffers.dstBuffer() : null,
                            meshData.idxSize() / VkUtils.INT_SIZE, meshData.materialId());
                    vulkanModel.getVulkanMeshList().add(vulkanMesh);

                    meshCount++;
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
