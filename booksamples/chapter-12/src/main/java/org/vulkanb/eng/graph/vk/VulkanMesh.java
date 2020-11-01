package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkBufferCopy;
import org.vulkanb.eng.graph.TextureCache;
import org.vulkanb.eng.scene.*;

import java.nio.*;

import static org.lwjgl.vulkan.VK11.*;

public class VulkanMesh {

    private String id;
    private VulkanBuffer indicesBuffer;
    private int indicesCount;
    private Material material;
    private Texture metalRoughTexture;
    private Texture normalMapTexture;
    private Texture texture;
    private VulkanBuffer verticesBuffer;

    public VulkanMesh(String id, VulkanBuffer verticesBuffer, VulkanBuffer indicesBuffer, int indicesCount,
                      Texture texture, Texture normalMapTexture, Texture metalRoughTexture, Material material) {
        this.id = id;
        this.verticesBuffer = verticesBuffer;
        this.indicesBuffer = indicesBuffer;
        this.indicesCount = indicesCount;
        this.texture = texture;
        this.normalMapTexture = normalMapTexture;
        this.metalRoughTexture = metalRoughTexture;
        this.material = material;
    }

    private static TransferBuffers createIndicesBuffers(Device device, MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * GraphConstants.INT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        IntBuffer data = MemoryUtil.memIntBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());
        data.put(indices);
        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static TransferBuffers createVerticesBuffers(Device device, MeshData meshData) {
        float[] positions = meshData.positions();
        float[] normals = meshData.normals();
        float[] tangents = meshData.tangents();
        float[] biTangents = meshData.biTangents();
        float[] textCoords = meshData.textCoords();
        if (textCoords == null || textCoords.length == 0) {
            textCoords = new float[(positions.length / 3) * 2];
        }
        int numElements = positions.length + normals.length + +tangents.length + biTangents.length + textCoords.length;
        int bufferSize = numElements * GraphConstants.FLOAT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);

        long mappedMemory = srcBuffer.map();
        FloatBuffer data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int rows = positions.length / 3;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 3;
            int startTextCoord = row * 2;
            data.put(positions[startPos]);
            data.put(positions[startPos + 1]);
            data.put(positions[startPos + 2]);
            data.put(normals[startPos]);
            data.put(normals[startPos + 1]);
            data.put(normals[startPos + 2]);
            data.put(tangents[startPos]);
            data.put(tangents[startPos + 1]);
            data.put(tangents[startPos + 2]);
            data.put(biTangents[startPos]);
            data.put(biTangents[startPos + 1]);
            data.put(biTangents[startPos + 2]);
            data.put(textCoords[startTextCoord]);
            data.put(textCoords[startTextCoord + 1]);
        }

        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    public static VulkanMesh[] loadMeshes(TextureCache textureCache, CommandPool commandPool, Queue queue, MeshData[] meshDataList) {
        int numMeshes = meshDataList != null ? meshDataList.length : 0;
        VulkanMesh[] meshes = new VulkanMesh[numMeshes];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = commandPool.getDevice();
            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
            cmd.beginRecording();

            VulkanBuffer[] positionTransferBuffers = new VulkanBuffer[numMeshes];
            VulkanBuffer[] indicesTransferBuffers = new VulkanBuffer[numMeshes];
            for (int i = 0; i < numMeshes; i++) {
                MeshData meshData = meshDataList[i];
                TransferBuffers verticesBuffers = createVerticesBuffers(device, meshData);
                TransferBuffers indicesBuffers = createIndicesBuffers(device, meshData);

                positionTransferBuffers[i] = verticesBuffers.srcBuffer();
                indicesTransferBuffers[i] = indicesBuffers.srcBuffer();

                Material material = meshData.material();
                String texturePath = material != null ? material.getTexturePath() : null;
                Texture texture = textureCache.createTexture(device, texturePath, VK_FORMAT_R8G8B8A8_SRGB);

                String normalMapPath = material != null ? material.getNormalMapPath() : null;
                Texture normalMapTexture = textureCache.createTexture(device, normalMapPath, VK_FORMAT_R8G8B8A8_UNORM);

                String metalRoughPath = material != null ? material.getMetalRoughPath() : null;
                Texture metalRougTexture = textureCache.createTexture(device, metalRoughPath, VK_FORMAT_R8G8B8A8_SRGB);

                meshes[i] = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(), indicesBuffers.dstBuffer(),
                        meshData.indices().length, texture, normalMapTexture, metalRougTexture, material);
                recordTransferCommand(cmd, verticesBuffers);
                recordTransferCommand(cmd, indicesBuffers);
                texture.recordTextureTransition(cmd);
                normalMapTexture.recordTextureTransition(cmd);
                metalRougTexture.recordTextureTransition(cmd);
            }

            cmd.endRecording();
            Fence fence = new Fence(device, true);
            fence.reset();
            queue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, fence);
            fence.fenceWait();
            fence.cleanup();
            cmd.cleanup();

            for (int i = 0; i < numMeshes; i++) {
                positionTransferBuffers[i].cleanup();
                indicesTransferBuffers[i].cleanup();
                meshes[i].getTexture().cleanupStgBuffer();
                meshes[i].getNormalMapTexture().cleanupStgBuffer();
                meshes[i].getMetalRoughTexture().cleanupStgBuffer();
            }
        }

        return meshes;
    }

    private static void recordTransferCommand(CommandBuffer cmd, TransferBuffers transferBuffers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer copyRegion = VkBufferCopy.callocStack(1, stack)
                    .srcOffset(0).dstOffset(0).size(transferBuffers.srcBuffer().getRequestedSize());
            vkCmdCopyBuffer(cmd.getVkCommandBuffer(), transferBuffers.srcBuffer().getBuffer(),
                    transferBuffers.dstBuffer().getBuffer(), copyRegion);
        }
    }

    public void cleanup() {
        indicesBuffer.cleanup();
        verticesBuffer.cleanup();
    }

    public String getId() {
        return id;
    }

    public VulkanBuffer getIndicesBuffer() {
        return indicesBuffer;
    }

    public int getIndicesCount() {
        return indicesCount;
    }

    public Material getMaterial() {
        return material;
    }

    public Texture getMetalRoughTexture() {
        return metalRoughTexture;
    }

    public Texture getNormalMapTexture() {
        return normalMapTexture;
    }

    public Texture getTexture() {
        return texture;
    }

    public VulkanBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    private record TransferBuffers(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer) {
    }
}