package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryUtil;
import org.tinylog.Logger;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.model.MaterialData;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

public class MaterialsCache {

    private static final int MATERIAL_SIZE = VkUtils.VEC4_SIZE * 3;
    private final IndexedLinkedHashMap<String, VulkanMaterial> materialsMap;
    private VkBuffer materialsBuffer;

    public MaterialsCache() {
        materialsMap = new IndexedLinkedHashMap<>();
    }

    public void cleanup(VkCtx vkCtx) {
        if (materialsBuffer != null) {
            materialsBuffer.cleanup(vkCtx);
        }
    }

    public VulkanMaterial getMaterial(String id) {
        return materialsMap.get(id);
    }

    public VkBuffer getMaterialsBuffer() {
        return materialsBuffer;
    }

    public int getPosition(String id) {
        int result = -1;
        if (id != null) {
            result = materialsMap.getIndexOf(id);
        } else {
            Logger.warn("Could not find material with id [{}]", id);
        }
        return result;
    }

    public void loadMaterials(VkCtx vkCtx, List<MaterialData> materials, TextureCache textureCache, CmdPool cmdPool,
                              Queue queue) {
        int numMaterials = materials.size();
        int bufferSize = MATERIAL_SIZE * numMaterials;

        var srcBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        materialsBuffer = new VkBuffer(vkCtx, bufferSize,
                VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);

        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmd.beginRecording();

        TransferBuffer transferBuffer = new TransferBuffer(srcBuffer, materialsBuffer);
        long mappedMemory = srcBuffer.map(vkCtx);
        ByteBuffer data = MemoryUtil.memByteBuffer(mappedMemory, (int) srcBuffer.getRequestedSize());

        int offset = 0;
        for (int i = 0; i < numMaterials; i++) {
            var material = materials.get(i);
            String texturePath = material.texturePath();
            boolean hasTexture = texturePath != null && !texturePath.isEmpty();
            boolean isTransparent;
            if (hasTexture) {
                Texture texture = textureCache.addTexture(vkCtx, texturePath, texturePath, VK_FORMAT_R8G8B8A8_SRGB);
                isTransparent = texture.isTransparent();
            } else {
                isTransparent = material.diffuseColor().w < 1.0f;
            }
            VulkanMaterial vulkanMaterial = new VulkanMaterial(material.id(), isTransparent);
            materialsMap.put(vulkanMaterial.id(), vulkanMaterial);

            material.diffuseColor().get(offset, data);
            data.putInt(offset + VkUtils.VEC4_SIZE, hasTexture ? 1 : 0);
            data.putInt(offset + VkUtils.VEC4_SIZE + VkUtils.INT_SIZE, textureCache.getPosition(texturePath));

            // Padding
            data.putInt(offset + VkUtils.VEC4_SIZE + VkUtils.INT_SIZE * 2, 0);
            data.putInt(offset + VkUtils.VEC4_SIZE + VkUtils.INT_SIZE * 3, 0);

            offset += MATERIAL_SIZE;
        }
        srcBuffer.unMap(vkCtx);

        transferBuffer.recordTransferCommand(cmd);

        cmd.endRecording();
        cmd.submitAndWait(vkCtx, queue);
        cmd.cleanup(vkCtx, cmdPool);

        transferBuffer.srcBuffer().cleanup(vkCtx);
    }
}
