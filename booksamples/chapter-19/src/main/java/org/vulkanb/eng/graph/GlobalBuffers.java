package org.vulkanb.eng.graph;

import org.lwjgl.system.*;
import org.lwjgl.vulkan.VkDrawIndirectCommand;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

// TODO: Support instances
public class GlobalBuffers {
    public static final int IND_COMMAND_STRIDE = VkDrawIndirectCommand.SIZEOF;
    private static final int INSTANCE_DATA_SIZE = VkUtils.MAT4X4_SIZE + VkUtils.INT_SIZE;

    private final VkBuffer[] buffIdxAddresses;
    private final VkBuffer[] buffIndirectDrawCmds;
    private final VkBuffer[] buffInstanceDataAddresses;
    private final VkBuffer[] buffVtxAddresses;
    private final int[] drawCounts;

    public GlobalBuffers() {
        buffVtxAddresses = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffIdxAddresses = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffInstanceDataAddresses = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
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
        Arrays.asList(buffInstanceDataAddresses).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffVtxAddresses).forEach(b -> b.cleanup(vkCtx));
        Arrays.asList(buffIdxAddresses).forEach(b -> b.cleanup(vkCtx));
    }

    private void createOrUpdateBuffers(VkCtx vkCtx, int frame, int numVtxAddresses, int numIdxAddresses, int numIndCommands) {
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
        int bufferSize = drawCounts[frame] * INSTANCE_DATA_SIZE;
        if (buffInstanceDataAddresses[frame] != null && buffInstanceDataAddresses[frame].getRequestedSize() < bufferSize) {
            buffInstanceDataAddresses[frame].cleanup(vkCtx);
            create = true;
        }
        if (buffInstanceDataAddresses[frame] == null || create) {
            buffInstanceDataAddresses[frame] = new VkBuffer(vkCtx, bufferSize,
                    VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT, VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        }
    }

    public long getAddrBufInstanceData(int currentFrame) {
        return buffInstanceDataAddresses[currentFrame].getAddress();
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
            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();

            var vtxAddresses = new ArrayList<Long>();
            var idxAddresses = new ArrayList<Long>();
            var indCommandList = new ArrayList<VkDrawIndirectCommand>();

            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                VulkanModel vulkanModel = modelsCache.getModel(entity.getModelId());
                List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                boolean animation = vulkanModel.hasAnimations();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    long vtxBufferAddress = animation ?
                            VkUtils.getBufferAddress(vkCtx, animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getBuffer()) :
                            vulkanMesh.verticesBuffer().getAddress();
                    vtxAddresses.add(vtxBufferAddress);
                    idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

                    var indCommand = VkDrawIndirectCommand.calloc(stack)
                            .vertexCount(vulkanMesh.numIndices())
                            .instanceCount(1)
                            .firstVertex(0)
                            .firstInstance(0);
                    indCommandList.add(indCommand);
                }
            }

            for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
                createOrUpdateBuffers(vkCtx, i, vtxAddresses.size(), idxAddresses.size(), indCommandList.size());
            }
        }
    }

    public void update(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, MaterialsCache materialsCache,
                       AnimationsCache animationsCache, int frame) {
        try (var stack = MemoryStack.stackPush()) {
            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();

            var vtxAddresses = new ArrayList<Long>();
            var idxAddresses = new ArrayList<Long>();
            var indCommandList = new ArrayList<VkDrawIndirectCommand>();

            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                VulkanModel vulkanModel = modelsCache.getModel(entity.getModelId());
                List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                boolean animation = vulkanModel.hasAnimations();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    long vtxBufferAddress = animation ?
                            VkUtils.getBufferAddress(vkCtx, animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getBuffer()) :
                            vulkanMesh.verticesBuffer().getAddress();
                    vtxAddresses.add(vtxBufferAddress);
                    idxAddresses.add(vulkanMesh.indicesBuffer().getAddress());

                    var indCommand = VkDrawIndirectCommand.calloc(stack)
                            .vertexCount(vulkanMesh.numIndices())
                            .instanceCount(1)
                            .firstVertex(0)
                            .firstInstance(0);
                    indCommandList.add(indCommand);
                }
            }

            createOrUpdateBuffers(vkCtx, frame, vtxAddresses.size(), idxAddresses.size(), indCommandList.size());

            updateAddresses(vkCtx, vtxAddresses, idxAddresses, frame);
            updateCmdIndirectCommands(vkCtx, indCommandList, frame);
            updateInstanceData(vkCtx, scene, modelsCache, materialsCache, frame);
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

    private void updateInstanceData(VkCtx vkCtx, Scene scene, ModelsCache modelsCache, MaterialsCache materialsCache,
                                    int frame) {
        var buffer = buffInstanceDataAddresses[frame];
        long mappedMemory = buffer.map(vkCtx);
        ByteBuffer dataBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) buffer.getRequestedSize());

        List<Entity> entities = scene.getEntities();
        int numEntities = entities.size();
        int offset = 0;
        for (int i = 0; i < numEntities; i++) {
            Entity entity = entities.get(i);
            VulkanModel vulkanModel = modelsCache.getModel(entity.getModelId());
            List<VulkanMesh> vulkanMeshList = vulkanModel.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                entity.getModelMatrix().get(offset, dataBuffer);
                offset += VkUtils.MAT4X4_SIZE;
                dataBuffer.putInt(offset, materialsCache.getPosition(vulkanMesh.materialdId()));
                offset += VkUtils.INT_SIZE;
            }
        }
        buffer.unMap(vkCtx);
    }
}