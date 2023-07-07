package org.vulkanb.eng.graph;

import org.vulkanb.eng.scene.Entity;

import java.util.*;

public class VulkanAnimEntity {
    private Entity entity;
    private List<VulkanAnimMesh> vulkanAnimMeshList;
    private VulkanModel vulkanModel;

    public VulkanAnimEntity(Entity entity, VulkanModel vulkanModel) {
        this.entity = entity;
        this.vulkanModel = vulkanModel;
        vulkanAnimMeshList = new ArrayList<>();
    }

    public Entity getEntity() {
        return entity;
    }

    public List<VulkanAnimMesh> getVulkanAnimMeshList() {
        return vulkanAnimMeshList;
    }

    public VulkanModel getVulkanModel() {
        return vulkanModel;
    }

    public record VulkanAnimMesh(int meshOffset, VulkanModel.VulkanMesh vulkanMesh) {
    }
}
