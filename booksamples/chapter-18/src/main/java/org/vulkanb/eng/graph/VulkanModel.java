package org.vulkanb.eng.graph;

import org.vulkanb.eng.graph.vk.VkCtx;

import java.util.*;

public class VulkanModel {

    private final String id;
    private final List<VulkanAnimation> vulkanAnimationList;
    private final List<VulkanMesh> vulkanMeshList;

    public VulkanModel(String id) {
        this.id = id;
        vulkanMeshList = new ArrayList<>();
        vulkanAnimationList = new ArrayList<>();
    }

    public void addVulkanAnimation(VulkanAnimation vulkanAnimation) {
        vulkanAnimationList.add(vulkanAnimation);
    }

    public void cleanup(VkCtx vkCtx) {
        vulkanMeshList.forEach(mesh -> mesh.cleanup(vkCtx));
        vulkanAnimationList.forEach(a -> a.cleanup(vkCtx));
    }

    public String getId() {
        return id;
    }

    public List<VulkanAnimation> getVulkanAnimationList() {
        return vulkanAnimationList;
    }

    public List<VulkanMesh> getVulkanMeshList() {
        return vulkanMeshList;
    }

    public boolean hasAnimations() {
        return !vulkanAnimationList.isEmpty();
    }
}