package org.vulkanb.eng.scene;

import java.util.List;

public class ModelData {
    private List<MeshData> meshDataList;
    private String modelId;

    public ModelData(String modelId, List<MeshData> meshDataList) {
        this.modelId = modelId;
        this.meshDataList = meshDataList;
    }

    public List<MeshData> getMeshDataList() {
        return meshDataList;
    }

    public String getModelId() {
        return modelId;
    }

    public record MeshData(float[] positions, float[] textCoords, int[] indices) {
    }
}