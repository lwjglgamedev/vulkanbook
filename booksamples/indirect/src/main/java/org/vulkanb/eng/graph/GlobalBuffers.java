package org.vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.scn.VtxBuffStruct;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;
import java.util.function.Predicate;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static org.vulkanb.eng.graph.vk.VkUtils.*;

// TODO: Check if we can use Models cache
// TODO: Check if we can use TransferBuffers instead of StagBuffers
public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndexedIndirectCommand.SIZEOF;
    private final VkBuffer animJointMatricesBuffer;
    private final VkBuffer animWeightsBuffer;
    private final VkBuffer indicesBuffer;
    private final VkBuffer verticesBuffer;
    private VkBuffer animIndirectBuffer;
    private VkBuffer[] animInstanceDataBuffers;
    private VkBuffer animVerticesBuffer;
    private VkBuffer indirectBuffer;
    private VkBuffer[] instanceDataBuffers;
    private int numAnimIndirectCommands;
    private int numIndirectCommands;
    private List<VulkanAnimEntity> vulkanAnimEntityList;

    public GlobalBuffers(VkCtx vkCtx) {
        Logger.debug("Creating global buffers");
        var engCfg = EngCfg.getInstance();
        verticesBuffer = new VkBuffer(vkCtx, engCfg.getMaxVerticesBuffer(), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT |
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
        indicesBuffer = new VkBuffer(vkCtx, engCfg.getMaxIndicesBuffer(), VK_BUFFER_USAGE_INDEX_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
        animJointMatricesBuffer = new VkBuffer(vkCtx, engCfg.getMaxJointMatricesBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
        animWeightsBuffer = new VkBuffer(vkCtx, engCfg.getMaxAnimWeightsBuffer(), VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
        numIndirectCommands = 0;
    }

    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying global buffers");
        verticesBuffer.cleanup(vkCtx);
        indicesBuffer.cleanup(vkCtx);
        if (indirectBuffer != null) {
            indirectBuffer.cleanup(vkCtx);
        }
        if (animVerticesBuffer != null) {
            animVerticesBuffer.cleanup(vkCtx);
        }
        if (animIndirectBuffer != null) {
            animIndirectBuffer.cleanup(vkCtx);
        }
        animJointMatricesBuffer.cleanup(vkCtx);
        animWeightsBuffer.cleanup(vkCtx);
        if (instanceDataBuffers != null) {
            Arrays.asList(instanceDataBuffers).forEach(b -> b.cleanup(vkCtx));
        }
        if (animInstanceDataBuffers != null) {
            Arrays.asList(animInstanceDataBuffers).forEach(b -> b.cleanup(vkCtx));
        }
    }

    public VkBuffer getAnimIndirectBuffer() {
        return animIndirectBuffer;
    }

    public VkBuffer[] getAnimInstanceDataBuffers() {
        return animInstanceDataBuffers;
    }

    public VkBuffer getAnimJointMatricesBuffer() {
        return animJointMatricesBuffer;
    }

    public VkBuffer getAnimVerticesBuffer() {
        return animVerticesBuffer;
    }

    public VkBuffer getAnimWeightsBuffer() {
        return animWeightsBuffer;
    }

    public VkBuffer getIndicesBuffer() {
        return indicesBuffer;
    }

    public VkBuffer getIndirectBuffer() {
        return indirectBuffer;
    }

    public VkBuffer[] getInstanceDataBuffers() {
        return instanceDataBuffers;
    }

    public int getNumAnimIndirectCommands() {
        return numAnimIndirectCommands;
    }

    public int getNumIndirectCommands() {
        return numIndirectCommands;
    }

    public VkBuffer getVerticesBuffer() {
        return verticesBuffer;
    }

    public List<VulkanAnimEntity> getVulkanAnimEntityList() {
        return vulkanAnimEntityList;
    }

    private void loadAnimEntities(VkCtx vkCtx, List<VulkanModel> vulkanModelList, Scene scene, CmdPool cmdPool,
                                  Queue queue) {
        vulkanAnimEntityList = new ArrayList<>();
        numAnimIndirectCommands = 0;
        try (var stack = MemoryStack.stackPush()) {
            if (animVerticesBuffer != null) {
                animVerticesBuffer.cleanup(vkCtx);
            }
            var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);

            int bufferOffset = 0;
            int firstInstance = 0;
            var indexedIndirectCommandList = new ArrayList<VkDrawIndexedIndirectCommand>();
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getId());
                if (entities == null || entities.isEmpty()) {
                    continue;
                }
                for (Entity entity : entities) {
                    if (!entity.hasAnimation()) {
                        continue;
                    }
                    var vulkanAnimEntity = new VulkanAnimEntity(entity, vulkanModel);
                    vulkanAnimEntityList.add(vulkanAnimEntity);
                    List<VulkanAnimEntity.VulkanAnimMesh> vulkanAnimMeshList = vulkanAnimEntity.getVulkanAnimMeshList();
                    for (VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                        VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                        indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                        indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / INT_SIZE);
                        indexedIndirectCommand.instanceCount(1);
                        indexedIndirectCommand.vertexOffset(bufferOffset / VtxBuffStruct.SIZE_IN_BYTES);
                        indexedIndirectCommand.firstInstance(firstInstance);
                        indexedIndirectCommandList.add(indexedIndirectCommand);

                        vulkanAnimMeshList.add(new VulkanAnimEntity.VulkanAnimMesh(bufferOffset, vulkanMesh));
                        bufferOffset += vulkanMesh.verticesSize();
                        firstInstance++;
                    }
                }
            }
            numAnimIndirectCommands = indexedIndirectCommandList.size();
            if (bufferOffset == 0 || numAnimIndirectCommands == 0) {
                return;
            }
            animVerticesBuffer = new VkBuffer(vkCtx, bufferOffset,
                    VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

            cmd.beginRecording();

            var indirectStgBuffer = new StgByteBuffer(vkCtx, (long) IND_COMMAND_STRIDE * numAnimIndirectCommands);
            if (animIndirectBuffer != null) {
                animIndirectBuffer.cleanup(vkCtx);
            }
            animIndirectBuffer = new VkBuffer(vkCtx, indirectStgBuffer.stgVkBuffer.getRequestedSize(),
                    VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
            ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
            VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

            indexedIndirectCommandList.forEach(indCommandBuffer::put);

            if (animInstanceDataBuffers != null) {
                Arrays.asList(animInstanceDataBuffers).forEach(b -> b.cleanup(vkCtx));
            }
            animInstanceDataBuffers = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
            for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
                animInstanceDataBuffers[i] = new VkBuffer(vkCtx,
                        (long) numAnimIndirectCommands * (MAT4X4_SIZE + INT_SIZE), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                        VMA_MEMORY_USAGE_AUTO,
                        VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            }

            indirectStgBuffer.recordTransferCommand(cmd, animIndirectBuffer);

            cmd.endRecording();
            cmd.submitAndWait(vkCtx, queue);
            cmd.cleanup(vkCtx, cmdPool);
            indirectStgBuffer.cleanup(vkCtx);
        }
    }

    private void loadAnimationData(ModelData modelData, VulkanModel vulkanModel, StgIntBuffer animJointMatricesStgBuffer) {
        List<Animation> animationsList = modelData.animationsList();
        if (!modelData.hasAnimations()) {
            return;
        }
        IntBuffer dataBuffer = animJointMatricesStgBuffer.getDataBuffer();
        for (Animation animation : animationsList) {
            var vulkanAnimationData = new VulkanAnimation();
            vulkanModel.addVulkanAnimation(vulkanAnimationData);
            List<AnimatedFrame> frameList = animation.frames();
            for (AnimatedFrame frame : frameList) {
                vulkanAnimationData.addVulkanAnimationFrame(new VulkanAnimationFrame(dataBuffer.position() * INT_SIZE));
                Matrix4f[] matrices = frame.jointMatrices();
                for (Matrix4f matrix : matrices) {
                    loadMatIntoIntBuffer(matrix, dataBuffer);
                }
            }
        }
    }

    public void loadEntities(VkCtx vkCtx, List<VulkanModel> vulkanModelList, Scene scene, CmdPool cmdPool,
                             Queue queue) {
        loadStaticEntities(vkCtx, vulkanModelList, scene, cmdPool, queue);
        loadAnimEntities(vkCtx, vulkanModelList, scene, cmdPool, queue);
    }

    public void loadInstanceData(VkCtx vkCtx, Scene scene, List<VulkanModel> vulkanModels, int currentFrame) {
        if (instanceDataBuffers != null) {
            Predicate<VulkanModel> excludeAnimatedEntitiesPredicate = VulkanModel::hasAnimations;
            loadInstanceData(vkCtx, scene, vulkanModels, instanceDataBuffers[currentFrame], excludeAnimatedEntitiesPredicate);
        }
        if (animInstanceDataBuffers != null) {
            Predicate<VulkanModel> excludedStaticEntitiesPredicate = v -> !v.hasAnimations();
            loadInstanceData(vkCtx, scene, vulkanModels, animInstanceDataBuffers[currentFrame], excludedStaticEntitiesPredicate);
        }
    }

    private void loadInstanceData(VkCtx vkCtx, Scene scene, List<VulkanModel> vulkanModels, VkBuffer instanceBuffer,
                                  Predicate<VulkanModel> excludedEntitiesPredicate) {
        if (instanceBuffer == null) {
            return;
        }
        long mappedMemory = instanceBuffer.map(vkCtx);
        ByteBuffer dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) instanceBuffer.getRequestedSize());
        int pos = 0;
        for (VulkanModel vulkanModel : vulkanModels) {
            List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getId());
            if (entities.isEmpty() || excludedEntitiesPredicate.test(vulkanModel)) {
                continue;
            }
            for (Entity entity : entities) {
                for (VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    entity.getModelMatrix().get(pos, dataBuffer);
                    pos += MAT4X4_SIZE;
                    dataBuffer.putInt(pos, vulkanMesh.materialIdx());
                    pos += INT_SIZE;
                }
            }
        }
        instanceBuffer.unMap(vkCtx);
    }

    private void loadMatIntoIntBuffer(Matrix4f m, IntBuffer buffer) {
        buffer.put(Float.floatToRawIntBits(m.m00()));
        buffer.put(Float.floatToRawIntBits(m.m01()));
        buffer.put(Float.floatToRawIntBits(m.m02()));
        buffer.put(Float.floatToRawIntBits(m.m03()));
        buffer.put(Float.floatToRawIntBits(m.m10()));
        buffer.put(Float.floatToRawIntBits(m.m11()));
        buffer.put(Float.floatToRawIntBits(m.m12()));
        buffer.put(Float.floatToRawIntBits(m.m13()));
        buffer.put(Float.floatToRawIntBits(m.m20()));
        buffer.put(Float.floatToRawIntBits(m.m21()));
        buffer.put(Float.floatToRawIntBits(m.m22()));
        buffer.put(Float.floatToRawIntBits(m.m23()));
        buffer.put(Float.floatToRawIntBits(m.m30()));
        buffer.put(Float.floatToRawIntBits(m.m31()));
        buffer.put(Float.floatToRawIntBits(m.m32()));
        buffer.put(Float.floatToRawIntBits(m.m33()));
    }

    private void loadMeshes(StgIntBuffer verticesStgBuffer, StgIntBuffer indicesStgBuffer, StgIntBuffer animWeightsStgBuffer,
                            ModelData modelData, VulkanModel vulkanModel, MaterialsCache materialsCache) {
        IntBuffer verticesData = verticesStgBuffer.getDataBuffer();
        IntBuffer indicesData = indicesStgBuffer.getDataBuffer();
        List<MeshData> meshDataList = modelData.meshes();
        int meshCount = 0;
        for (MeshData meshData : meshDataList) {
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
            int verticesSize = numElements * FLOAT_SIZE;

            int materialIdx = materialsCache.getPosition(meshData.materialId());
            vulkanModel.addVulkanMesh(new VulkanMesh(verticesSize, indices.length,
                    verticesData.position() * INT_SIZE, indicesData.position() * INT_SIZE,
                    materialIdx, animWeightsStgBuffer.getDataBuffer().position() * INT_SIZE));

            int rows = positions.length / 3;
            for (int row = 0; row < rows; row++) {
                int startPos = row * 3;
                int startTextCoord = row * 2;
                verticesData.put(Float.floatToRawIntBits(positions[startPos]));
                verticesData.put(Float.floatToRawIntBits(positions[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(positions[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(normals[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(tangents[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos + 1]));
                verticesData.put(Float.floatToRawIntBits(biTangents[startPos + 2]));
                verticesData.put(Float.floatToRawIntBits(textCoords[startTextCoord]));
                verticesData.put(Float.floatToRawIntBits(textCoords[startTextCoord + 1]));
            }

            Arrays.stream(indices).forEach(indicesData::put);

            loadWeightsBuffer(modelData, animWeightsStgBuffer, meshCount);
            meshCount++;
        }
    }

    public List<VulkanModel> loadModels(VkCtx vkCtx, List<ModelData> modelDataList, MaterialsCache materialsCache,
                                        TextureCache textureCache, CmdPool cmdPool, Queue queue) {
        List<VulkanModel> vulkanModelList = new ArrayList<>();
        List<Texture> textureList = new ArrayList<>();

        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);

        StgIntBuffer verticesStgBuffer = new StgIntBuffer(vkCtx, verticesBuffer.getRequestedSize());
        StgIntBuffer indicesStgBuffer = new StgIntBuffer(vkCtx, indicesBuffer.getRequestedSize());
        StgIntBuffer animJointMatricesStgBuffer = new StgIntBuffer(vkCtx, animJointMatricesBuffer.getRequestedSize());
        StgIntBuffer animWeightsStgBuffer = new StgIntBuffer(vkCtx, animWeightsBuffer.getRequestedSize());

        cmd.beginRecording();

        for (ModelData modelData : modelDataList) {
            VulkanModel vulkanModel = new VulkanModel(modelData.id());
            vulkanModelList.add(vulkanModel);
            loadMeshes(verticesStgBuffer, indicesStgBuffer, animWeightsStgBuffer, modelData, vulkanModel, materialsCache);
            loadAnimationData(modelData, vulkanModel, animJointMatricesStgBuffer);
        }

        // We need to ensure that at least we have one texture
        if (textureList.isEmpty()) {
            var engCfg = EngCfg.getInstance();
            Texture defaultTexture = textureCache.addTexture(vkCtx, engCfg.getDefaultTexturePath(),
                    engCfg.getDefaultTexturePath(), VK_FORMAT_R8G8B8A8_SRGB);
            textureList.add(defaultTexture);
        }

        verticesStgBuffer.recordTransferCommand(cmd, verticesBuffer);
        indicesStgBuffer.recordTransferCommand(cmd, indicesBuffer);
        animJointMatricesStgBuffer.recordTransferCommand(cmd, animJointMatricesBuffer);
        animWeightsStgBuffer.recordTransferCommand(cmd, animWeightsBuffer);
        textureList.forEach(t -> t.recordTextureTransition(cmd));
        cmd.endRecording();

        cmd.submitAndWait(vkCtx, queue);
        cmd.cleanup(vkCtx, cmdPool);

        verticesStgBuffer.cleanup(vkCtx);
        indicesStgBuffer.cleanup(vkCtx);
        animJointMatricesStgBuffer.cleanup(vkCtx);
        animWeightsStgBuffer.cleanup(vkCtx);
        textureList.forEach(t -> t.cleanupStgBuffer(vkCtx));

        return vulkanModelList;
    }

    private void loadStaticEntities(VkCtx vkCtx, List<VulkanModel> vulkanModelList, Scene scene, CmdPool cmdPool,
                                    Queue queue) {
        numIndirectCommands = 0;
        try (var stack = MemoryStack.stackPush()) {
            var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);

            var indexedIndirectCommandList = new ArrayList<VkDrawIndexedIndirectCommand>();
            int numInstances = 0;
            int firstInstance = 0;
            for (VulkanModel vulkanModel : vulkanModelList) {
                List<Entity> entities = scene.getEntitiesByModelId(vulkanModel.getId());
                if (entities.isEmpty() || vulkanModel.hasAnimations()) {
                    continue;
                }
                for (VulkanMesh vulkanMesh : vulkanModel.getVulkanMeshList()) {
                    VkDrawIndexedIndirectCommand indexedIndirectCommand = VkDrawIndexedIndirectCommand.calloc(stack);
                    indexedIndirectCommand.indexCount(vulkanMesh.numIndices());
                    indexedIndirectCommand.firstIndex(vulkanMesh.indicesOffset() / INT_SIZE);
                    indexedIndirectCommand.instanceCount(entities.size());
                    indexedIndirectCommand.vertexOffset(vulkanMesh.verticesOffset() / VtxBuffStruct.SIZE_IN_BYTES);
                    indexedIndirectCommand.firstInstance(firstInstance);
                    indexedIndirectCommandList.add(indexedIndirectCommand);

                    numIndirectCommands++;
                    firstInstance += entities.size();
                    numInstances += entities.size();
                }
            }
            if (numIndirectCommands == 0) {
                return;
            }
            cmd.beginRecording();

            var indirectStgBuffer = new StgByteBuffer(vkCtx, (long) IND_COMMAND_STRIDE * numIndirectCommands);
            if (indirectBuffer != null) {
                indirectBuffer.cleanup(vkCtx);
            }
            indirectBuffer = new VkBuffer(vkCtx, indirectStgBuffer.stgVkBuffer.getRequestedSize(),
                    VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
            ByteBuffer dataBuffer = indirectStgBuffer.getDataBuffer();
            VkDrawIndexedIndirectCommand.Buffer indCommandBuffer = new VkDrawIndexedIndirectCommand.Buffer(dataBuffer);

            indexedIndirectCommandList.forEach(indCommandBuffer::put);

            if (instanceDataBuffers != null) {
                Arrays.asList(instanceDataBuffers).forEach(b -> b.cleanup(vkCtx));
            }
            instanceDataBuffers = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
            for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
                instanceDataBuffers[i] = new VkBuffer(vkCtx, (long) numInstances * (MAT4X4_SIZE + INT_SIZE),
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                        VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            }

            indirectStgBuffer.recordTransferCommand(cmd, indirectBuffer);

            cmd.endRecording();
            cmd.submitAndWait(vkCtx, queue);
            cmd.cleanup(vkCtx, cmdPool);
            indirectStgBuffer.cleanup(vkCtx);
        }
    }

    private void loadWeightsBuffer(ModelData modelData, StgIntBuffer animWeightsBuffer, int meshCount) {
        List<AnimMeshData> animMeshDataList = modelData.animMeshDataList();
        if (animMeshDataList == null || animMeshDataList.isEmpty()) {
            return;
        }

        AnimMeshData animMeshData = animMeshDataList.get(meshCount);
        float[] weights = animMeshData.weights();
        int[] boneIds = animMeshData.boneIds();

        IntBuffer dataBuffer = animWeightsBuffer.getDataBuffer();

        int rows = weights.length / 4;
        for (int row = 0; row < rows; row++) {
            int startPos = row * 4;
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 1]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 2]));
            dataBuffer.put(Float.floatToRawIntBits(weights[startPos + 3]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 1]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 2]));
            dataBuffer.put(Float.floatToRawIntBits(boneIds[startPos + 3]));
        }
    }

    private static abstract class StgBuffer {
        protected final VkBuffer stgVkBuffer;

        public StgBuffer(VkCtx vkCtx, long size) {
            stgVkBuffer = new VkBuffer(vkCtx, size, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }

        public void cleanup(VkCtx vkCtx) {
            stgVkBuffer.unMap(vkCtx);
            stgVkBuffer.cleanup(vkCtx);
        }

        public void recordTransferCommand(CmdBuffer cmd, VkBuffer dstBuffer) {
            try (var stack = MemoryStack.stackPush()) {
                VkBufferCopy.Buffer copyRegion = VkBufferCopy.calloc(1, stack)
                        .srcOffset(0).dstOffset(0).size(stgVkBuffer.getRequestedSize());
                vkCmdCopyBuffer(cmd.getVkCommandBuffer(), stgVkBuffer.getBuffer(), dstBuffer.getBuffer(), copyRegion);
            }
        }
    }

    private static class StgByteBuffer extends StgBuffer {
        private final ByteBuffer dataBuffer;

        public StgByteBuffer(VkCtx vkCtx, long size) {
            super(vkCtx, size);
            long mappedMemory = stgVkBuffer.map(vkCtx);
            dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stgVkBuffer.getRequestedSize());
        }

        public ByteBuffer getDataBuffer() {
            return dataBuffer;
        }
    }

    private static class StgIntBuffer extends StgBuffer {
        private final IntBuffer dataBuffer;

        public StgIntBuffer(VkCtx vkCtx, long size) {
            super(vkCtx, size);
            long mappedMemory = stgVkBuffer.map(vkCtx);
            dataBuffer = MemoryUtil.memIntBuffer(mappedMemory, (int) stgVkBuffer.getRequestedSize() / INT_SIZE);
        }

        public IntBuffer getDataBuffer() {
            return dataBuffer;
        }
    }
}
