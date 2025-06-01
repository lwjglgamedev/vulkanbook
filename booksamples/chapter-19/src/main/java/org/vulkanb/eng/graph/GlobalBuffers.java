package org.vulkanb.eng.graph;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkDrawIndirectCommand;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

// TODO: Avoid repetition loadEntities vs update
public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndirectCommand.SIZEOF;
    private static final int INSTANCE_DATA_SIZE = VkUtils.INT_SIZE * 4;

    private final VkBuffer[] buffIdxAddresses;
    private final VkBuffer[] buffIndirectDrawCmds;
    private final VkBuffer[] buffInstanceData;
    private final VkBuffer[] buffModelMatrices;
    private final VkBuffer[] buffVtxAddresses;
    private final int[] drawCounts;

    public GlobalBuffers() {
        buffVtxAddresses = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffIdxAddresses = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffInstanceData = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffModelMatrices = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffIndirectDrawCmds = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        drawCounts = new int[VkUtils.MAX_IN_FLIGHT];
    }

    private static void updateAddressBuff(VkCtx vkCtx, VkBuffer buffer, List<Long> addressesList) {
        int size = addressesList.size();
        LongBuffer dataBuff = MemoryUtil.memLongBuffer(buffer.map(vkCtx), (int) buffer.getRequestedSize());
        for (int i = 0; i < size; i++) {
            long address = addressesList.get(i);
            dataBuff.put(i, address);
        }
        buffer.unMap(vkCtx);
    }

    public void cleanup(VkCtx vkCtx) {
        Arrays.asList(buffIndirectDrawCmds).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffModelMatrices).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffInstanceData).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffVtxAddresses).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffIdxAddresses).forEach(b -> b.cleanup(vkCtx));
    }

    private void createOrUpdateBuffers(VkCtx vkCtx, int frame, int numVtxAddresses, int numIdxAddresses,
                                       int numIndCommands, int numInstanceData, int numEntities) {
        int buffVtxAddressSize = numVtxAddresses * VkUtils.PTR_SIZE;
        int buffIdxAddressSize = numIdxAddresses * VkUtils.PTR_SIZE;
        boolean create = false;
        if (buffVtxAddresses[frame] != null && buffVtxAddresses[frame].getRequestedSize() < buffVtxAddressSize) {
            buffVtxAddresses[frame].cleanup(vkCtx);
            buffIdxAddresses[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffVtxAddresses[frame] == null || create) {
            buffVtxAddresses[frame] = new VkBuffer(vkCtx, (long) buffVtxAddressSize * VkUtils.PTR_SIZE,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);

            buffIdxAddresses[frame] = new VkBuffer(vkCtx, (long) buffIdxAddressSize * VkUtils.PTR_SIZE,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }

        create = false;
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

    public long getAddrBuffIdxAddresses(int currentFrame) {
        return buffIdxAddresses[currentFrame].getAddress();
    }

    public long getAddrBuffVtxAddresses(int currentFrame) {
        return buffVtxAddresses[currentFrame].getAddress();
    }

    public int getDrawCount(int currentFrame) {
        return drawCounts[currentFrame];
    }

    public VkBuffer getIndirectBuffer(int currentFrame) {
        return buffIndirectDrawCmds[currentFrame];
    }

    public void loadEntities(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, AnimationsCache animationsCache) {

        try (var stack = MemoryStack.stackPush()) {
            Map<String, List<Entity>> entitiesMap = scene.getEntities();
            var vtxAddresses = new ArrayList<Long>();
            var idxAddresses = new ArrayList<Long>();
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
                    for (int i = 0; i < numEntities; i++) {
                        var entity = entities.get(i);
                        for (int j = 0; j < numMeshes; j++) {
                            var vulkanMesh = vulkanMeshList.get(j);
                            long vtxBufferAddress = animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getBuffer();
                            vtxAddresses.add(vtxBufferAddress);
                            idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

                            var indCommand = VkDrawIndirectCommand.calloc(stack)
                                    .vertexCount(vulkanMesh.numIndices())
                                    .instanceCount(1)
                                    .firstVertex(0)
                                    .firstInstance(0);
                            indCommandList.add(indCommand);
                            baseEntityIdx++;
                            numInstanceData++;
                        }
                    }
                } else {
                    for (int j = 0; j < numMeshes; j++) {
                        var vulkanMesh = vulkanMeshList.get(j);
                        long vtxBufferAddress = vulkanMesh.verticesBuffer().getAddress();
                        vtxAddresses.add(vtxBufferAddress);
                        idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

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
            for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
                createOrUpdateBuffers(vkCtx, i, vtxAddresses.size(), idxAddresses.size(), indCommandList.size(),
                        numInstanceData, totalEntities);
            }
        }
    }

    public void update(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, MaterialsCache materialsCache,
                       AnimationsCache animationsCache, int frame) {
        try (var stack = MemoryStack.stackPush()) {
            Map<String, List<Entity>> entitiesMap = scene.getEntities();
            var vtxAddresses = new ArrayList<Long>();
            var idxAddresses = new ArrayList<Long>();
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
                    for (int i = 0; i < numEntities; i++) {
                        var entity = entities.get(i);
                        for (int j = 0; j < numMeshes; j++) {
                            var vulkanMesh = vulkanMeshList.get(j);
                            long vtxBufferAddress = animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress();
                            vtxAddresses.add(vtxBufferAddress);
                            idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

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
                        long vtxBufferAddress = vulkanMesh.verticesBuffer().getAddress();
                        vtxAddresses.add(vtxBufferAddress);
                        idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

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
            createOrUpdateBuffers(vkCtx, frame, vtxAddresses.size(), idxAddresses.size(), indCommandList.size(),
                    numInstanceData, totalEntities);

            updateAddresses(vkCtx, vtxAddresses, idxAddresses, frame);
            updateCmdIndirectCommands(vkCtx, indCommandList, frame);
            updateInstanceData(vkCtx, entitiesMap, modelsCache, materialsCache, frame);
        }
    }

    private void updateAddresses(VkCtx vkCtx, List<Long> vtxAddresses, List<Long> idxAddresses, int currentFrame) {
        updateAddressBuff(vkCtx, buffVtxAddresses[currentFrame], vtxAddresses);
        updateAddressBuff(vkCtx, buffIdxAddresses[currentFrame], idxAddresses);
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
                                    MaterialsCache materialsCache, int frame) {
        var bufferInstances = buffInstanceData[frame];
        long mappedMemory = bufferInstances.map(vkCtx);
        ByteBuffer instanceData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferInstances.getRequestedSize());

        var bufferModels = buffModelMatrices[frame];
        mappedMemory = bufferModels.map(vkCtx);
        ByteBuffer matricesData = MemoryUtil.memByteBuffer(mappedMemory, (int) bufferModels.getRequestedSize());

        int baseEntities = 0;
        int offset = 0;
        int meshIdx = 0;
        for (String modelId : entitiesMap.keySet()) {
            List<Entity> entities = entitiesMap.get(modelId);
            int numEntities = entities.size();

            VulkanModel vulkanModel = modelsCache.getModel(modelId);
            List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int i = 0; i < numMeshes; i++) {
                for (int j = 0; j < numEntities; j++) {
                    Entity entity = entities.get(j);
                    int entityIdx = baseEntities + j;
                    entity.getModelMatrix().get(entityIdx * VkUtils.MAT4X4_SIZE, matricesData);

                    instanceData.putInt(offset, entityIdx);
                    offset += VkUtils.INT_SIZE;
                    var vulkanMesh = vulkanMeshList.get(i);
                    instanceData.putInt(offset, materialsCache.getPosition(vulkanMesh.materialdId()));
                    offset += VkUtils.INT_SIZE;
                    instanceData.putInt(offset, meshIdx);
                    offset += VkUtils.INT_SIZE;
                    instanceData.putInt(offset, 0);
                    offset += VkUtils.INT_SIZE;
                }
                meshIdx++;
            }
            baseEntities += numEntities;
        }

        bufferInstances.unMap(vkCtx);
        bufferModels.unMap(vkCtx);
    }
}