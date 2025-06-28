package org.vulkanb.eng.graph;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkDrawIndirectCommand;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndirectCommand.SIZEOF;
    private static final int INSTANCE_DATA_SIZE = VkUtils.INT_SIZE * 2 + VkUtils.PTR_SIZE * 2;

    private final VkBuffer[] buffIndirectDrawCmds;
    private final VkBuffer[] buffInstanceData;
    private final VkBuffer[] buffModelMatrices;
    private final int[] drawCounts;

    public GlobalBuffers() {
        buffInstanceData = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffModelMatrices = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffIndirectDrawCmds = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        drawCounts = new int[VkUtils.MAX_IN_FLIGHT];
    }

    public void cleanup(VkCtx vkCtx) {
        Arrays.asList(buffIndirectDrawCmds).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffModelMatrices).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffInstanceData).forEach(b -> b.cleanup(vkCtx));
    }

    private void createOrUpdateBuffers(VkCtx vkCtx, int frame, int numIndCommands, int numInstanceData, int numEntities) {
        boolean create = false;
        if (buffIndirectDrawCmds[frame] != null && drawCounts[frame] < numIndCommands) {
            buffIndirectDrawCmds[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffIndirectDrawCmds[frame] == null || create) {
            buffIndirectDrawCmds[frame] = new VkBuffer(vkCtx, (long) IND_COMMAND_STRIDE * numIndCommands,
                    VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
        drawCounts[frame] = numIndCommands;

        create = false;
        int bufferSize = numInstanceData * INSTANCE_DATA_SIZE;
        if (buffInstanceData[frame] != null && buffInstanceData[frame].getRequestedSize() < bufferSize) {
            buffInstanceData[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffInstanceData[frame] == null || create) {
            buffInstanceData[frame] = new VkBuffer(vkCtx, bufferSize,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }

        create = false;
        bufferSize = numEntities * VkUtils.MAT4X4_SIZE;
        if (buffModelMatrices[frame] != null && buffModelMatrices[frame].getRequestedSize() < bufferSize) {
            buffModelMatrices[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffModelMatrices[frame] == null || create) {
            buffModelMatrices[frame] = new VkBuffer(vkCtx, bufferSize,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
    }

    public long getAddrBufInstanceData(int currentFrame) {
        return buffInstanceData[currentFrame].getAddress();
    }

    public long getAddrBufModeMatrices(int currentFrame) {
        return buffModelMatrices[currentFrame].getAddress();
    }

    public int getDrawCount(int currentFrame) {
        return drawCounts[currentFrame];
    }

    public VkBuffer getIndirectBuffer(int currentFrame) {
        return buffIndirectDrawCmds[currentFrame];
    }

    public void update(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, AnimationsCache animationsCache,
                       MaterialsCache materialsCache, int frame) {
        try (var stack = MemoryStack.stackPush()) {
            Map<String, List<Entity>> entitiesMap = scene.getEntities();
            var indCommandList = new ArrayList<VkDrawIndirectCommand>();

            int baseEntityIdx = 0;
            int numInstanceData = 0;
            for (String key : entitiesMap.keySet()) {
                var entities = entitiesMap.get(key);
                int numEntities = entities.size();
                VulkanModel vulkanModel = modelsCache.getModel(key);
                List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                if (vulkanModel.hasAnimations()) {
                    for (int i = 0; i < numMeshes; i++) {
                        for (int j = 0; j < numEntities; j++) {
                            var vulkanMesh = vulkanMeshList.get(i);

                            var indCommand = VkDrawIndirectCommand.calloc(stack)
                                    .vertexCount(vulkanMesh.numIndices())
                                    .instanceCount(1)
                                    .firstVertex(0)
                                    .firstInstance(baseEntityIdx);
                            indCommandList.add(indCommand);
                            baseEntityIdx++;
                            numInstanceData++;
                        }
                    }
                } else {
                    for (int j = 0; j < numMeshes; j++) {
                        var vulkanMesh = vulkanMeshList.get(j);
                        var indCommand = VkDrawIndirectCommand.calloc(stack)
                                .vertexCount(vulkanMesh.numIndices())
                                .instanceCount(numEntities)
                                .firstVertex(0)
                                .firstInstance(baseEntityIdx);
                        indCommandList.add(indCommand);
                        baseEntityIdx += numEntities;
                        numInstanceData += numEntities;
                    }
                }
            }
            int totalEntities = scene.getNumEntities();
            createOrUpdateBuffers(vkCtx, frame, indCommandList.size(), numInstanceData, totalEntities);

            updateCmdIndirectCommands(vkCtx, indCommandList, frame);
            updateInstanceData(vkCtx, entitiesMap, modelsCache, materialsCache, animationsCache, frame);
        }
    }

    private void updateCmdIndirectCommands(VkCtx vkCtx, List<VkDrawIndirectCommand> indCommandList, int frame) {
        int numCommands = indCommandList.size();
        var buffer = buffIndirectDrawCmds[frame];
        ByteBuffer dataBuff = MemoryUtil.memByteBuffer(buffer.map(vkCtx), (int) buffer.getRequestedSize());
        VkDrawIndirectCommand.Buffer indCommandBuffer = new VkDrawIndirectCommand.Buffer(dataBuff);
        for (int i = 0; i < numCommands; i++) {
            indCommandBuffer.put(i, indCommandList.get(i));
        }
        buffer.unMap(vkCtx);
    }

    private void updateInstanceData(VkCtx vkCtx, Map<String, List<Entity>> entitiesMap, ModelsCache modelsCache,
                                    MaterialsCache materialsCache, AnimationsCache animationsCache, int frame) {
        var bufferInstances = buffInstanceData[frame];
        long mappedMemory = bufferInstances.map(vkCtx);
        ByteBuffer instanceData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferInstances.getRequestedSize());

        var bufferModels = buffModelMatrices[frame];
        mappedMemory = bufferModels.map(vkCtx);
        ByteBuffer matricesData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferModels.getRequestedSize());

        int baseEntities = 0;
        int offset = 0;
        for (String modelId : entitiesMap.keySet()) {
            List<Entity> entities = entitiesMap.get(modelId);
            int numEntities = entities.size();

            VulkanModel vulkanModel = modelsCache.getModel(modelId);
            boolean animatedModel = vulkanModel.hasAnimations();
            List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int i = 0; i < numMeshes; i++) {
                var mesh = vulkanMeshList.get(i);
                long vtxBufferAddress = mesh.verticesBuffer().getAddress();
                long idxBufferAddress = mesh.indicesBuffer().getAddress();
                for (int j = 0; j < numEntities; j++) {
                    Entity entity = entities.get(j);
                    int entityIdx = baseEntities + j;
                    entity.getModelMatrix().get(entityIdx * VkUtils.MAT4X4_SIZE, matricesData);

                    instanceData.putInt(offset, entityIdx);
                    offset += VkUtils.INT_SIZE;
                    var vulkanMesh = vulkanMeshList.get(i);
                    instanceData.putInt(offset, materialsCache.getPosition(vulkanMesh.materialdId()));
                    offset += VkUtils.INT_SIZE;
                    if (animatedModel) {
                        vtxBufferAddress = animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress();
                    }
                    instanceData.putLong(offset, vtxBufferAddress);
                    offset += VkUtils.PTR_SIZE;
                    instanceData.putLong(offset, idxBufferAddress);
                    offset += VkUtils.PTR_SIZE;
                }
            }
            baseEntities += numEntities;
        }

        bufferInstances.unMap(vkCtx);
        bufferModels.unMap(vkCtx);
    }
}