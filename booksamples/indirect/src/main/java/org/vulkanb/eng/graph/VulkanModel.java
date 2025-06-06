package org.vulkanb.eng.graph;

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

    public void addVulkanAnimation(VulkanAnimation vulkanAnimationData) {
        vulkanAnimationList.add(vulkanAnimationData);
    }

    public void addVulkanMesh(VulkanMesh vulkanMesh) {
        vulkanMeshList.add(vulkanMesh);
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