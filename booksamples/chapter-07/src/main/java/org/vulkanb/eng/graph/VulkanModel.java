package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.VkCtx;

import java.util.*;

public class VulkanModel {

    private final String id;
    private final List<VulkanMesh> vulkanMeshList;

    public VulkanModel(String id) {
        this.id = id;
        vulkanMeshList = new ArrayList<>();
    }

    public void cleanup(VkCtx vkCtx) {
        vulkanMeshList.forEach(mesh -> mesh.cleanup(vkCtx));
    }

    public String getId() {
        return id;
    }

    public List<VulkanMesh> getVulkanMeshList() {
        return vulkanMeshList;
    }
}