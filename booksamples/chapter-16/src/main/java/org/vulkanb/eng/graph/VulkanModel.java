package org.vulkanb.eng.graph;

import java.util.*;

public class VulkanModel {

    private final String modelId;

    private final List<VulkanModel.VulkanMesh> vulkanMeshList;

    public VulkanModel(String modelId) {
        this.modelId = modelId;
        vulkanMeshList = new ArrayList<>();
    }

    public void addVulkanMesh(VulkanModel.VulkanMesh vulkanMesh) {
        vulkanMeshList.add(vulkanMesh);
    }

    public String getModelId() {
        return modelId;
    }

    public List<VulkanModel.VulkanMesh> getVulkanMeshList() {
        return vulkanMeshList;
    }

    public static record VulkanMaterial(int globalMaterialIdx) {
    }

    public static record VulkanMesh(int verticesSize, int numIndices, int verticesOffset, int indicesOffset,
                                    int globalMaterialIdx) {
    }
}