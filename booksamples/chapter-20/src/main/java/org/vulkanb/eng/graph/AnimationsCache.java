package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Entity;

import java.util.*;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO;
import static org.lwjgl.vulkan.VK13.*;

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

    public void loadAnimations(VkCtx vkCtx, Map<String, List<Entity>> entities, ModelsCache modelsCache) {
        for (var list : entities.values()) {
            int size = list.size();
            for (int i = 0; i < size; i++) {
                var entity = list.get(i);
                VulkanModel model = modelsCache.getModel(entity.getModelId());
                if (model == null) {
                    throw new RuntimeException("Model [" + entity.getModelId() + "] not found");
                }
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
                            VMA_MEMORY_USAGE_AUTO, 0, 0);
                    bufferList.put(vulkanMesh.id(), animationBuffer);
                }
            }
        }
    }
}
