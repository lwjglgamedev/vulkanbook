package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Entity;

import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

public class AnimationsCache {

    private final Map<String, Map<String, VkBuffer>> entitiesAnimBuffers;

    public AnimationsCache() {
        entitiesAnimBuffers = new HashMap<>();
    }

    public void cleanup(VkCtx vkCtx) {
        entitiesAnimBuffers.values().forEach(m -> m.values().forEach(b -> b.cleanup(vkCtx)));
    }

    public VkBuffer getBuffer(String entityId, String meshId) {
        return entitiesAnimBuffers.get(entityId).get(meshId);
    }

    public void loadAnimations(VkCtx vkCtx, List<Entity> entities, ModelsCache modelsCache) {
        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            var entity = entities.get(i);
            VulkanModel model = modelsCache.getModel(entity.getModelId());
            if (!model.hasAnimations()) {
                continue;
            }
            Map<String, VkBuffer> bufferList = new HashMap<>();
            entitiesAnimBuffers.put(entity.getId(), bufferList);

            List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                VkBuffer animationBuffer = new VkBuffer(vkCtx, vulkanMesh.verticesBuffer().getRequestedSize(),
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                        VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT, 0);
                bufferList.put(vulkanMesh.id(), animationBuffer);
            }
        }
    }
}
