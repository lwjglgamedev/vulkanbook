package org.vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.vulkanb.eng.graph.TextureCache;
import org.vulkanb.eng.scene.*;

import java.nio.*;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class VulkanMesh {

    private String id;
    private VulkanBuffer indicesBuffer;
    private int indicesCount;
    private String textureId;
    private VulkanBuffer verticesBuffer;

    public VulkanMesh(String id, VulkanBuffer verticesBuffer, VulkanBuffer indicesBuffer, int indicesCount, String textureId) {
        this.id = id;
        this.verticesBuffer = verticesBuffer;
        this.indicesBuffer = indicesBuffer;
        this.indicesCount = indicesCount;
        this.textureId = textureId;
    }

    private static TransferBuffers createIndicesBuffers(Device device, MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * GraphConstants.INT_LENGTH;

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

    private static TransferBuffers createVerticesBuffers(Device device, MeshData meshData) {
        float[] positions = meshData.positions();
        float[] textCoords = meshData.textCoords();
        if (textCoords == null || textCoords.length == 0) {
            textCoords = new float[(positions.length / 3) * 2];
        }
        int numElements = positions.length + textCoords.length;
        int bufferSize = numElements * GraphConstants.FLOAT_LENGTH;

        VulkanBuffer srcBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VulkanBuffer dstBuffer = new VulkanBuffer(device, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);
            vkCheck(vkMapMemory(device.getVkDevice(), srcBuffer.getMemory(), 0, srcBuffer.getAllocationSize(), 0, pp),
                    "Failed to map memory");

            int rows = positions.length / 3;
            FloatBuffer data = pp.getFloatBuffer(0, numElements);
            for (int row = 0; row < rows; row++) {
                int startPos = row * 3;
                int startTextCoord = row * 2;
                data.put(positions[startPos]);
                data.put(positions[startPos + 1]);
                data.put(positions[startPos + 2]);
                data.put(textCoords[startTextCoord]);
                data.put(textCoords[startTextCoord + 1]);
            }

            vkUnmapMemory(device.getVkDevice(), srcBuffer.getMemory());
        }

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
                Texture texture = textureCache.createTexture(commandPool, queue, material.getTexturePath(),
                        VK_FORMAT_R8G8B8A8_SRGB);

                meshes[i] = new VulkanMesh(meshData.id(), verticesBuffers.dstBuffer(), indicesBuffers.dstBuffer(),
                        meshData.indices().length, texture.getFileName());
                recordTransferCommand(cmd, verticesBuffers);
                recordTransferCommand(cmd, indicesBuffers);
            }

            cmd.endRecording();
            Fence fence = new Fence(device, true);
            fence.reset();
            queue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, fence);
            fence.fenceWait();
            fence.cleanup();

            for (int i = 0; i < numMeshes; i++) {
                positionTransferBuffers[i].cleanup();
                indicesTransferBuffers[i].cleanup();
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

    public String getTextureId() {
        return textureId;
    }

    public VulkanBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    private record TransferBuffers(VulkanBuffer srcBuffer, VulkanBuffer dstBuffer) {
    }
}