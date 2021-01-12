package org.vulkanb.eng.scene;

import org.joml.*;

import java.util.List;

public class ModelData {
    private List<Material> materialList;
    private List<MeshData> meshDataList;
    private String modelId;

    public ModelData(String modelId, List<MeshData> meshDataList, List<Material> materialList) {
        this.modelId = modelId;
        this.meshDataList = meshDataList;
        this.materialList = materialList;
    }

    public List<Material> getMaterialList() {
        return materialList;
    }

    public List<MeshData> getMeshDataList() {
        return meshDataList;
    }

    public String getModelId() {
        return modelId;
    }

    public record Material(String texturePath, String normalMapPath, String metalRoughMap, Vector4f diffuseColor,
                           float roughnessFactor, float metallicFactor) {
        public static final Vector4f DEFAULT_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

        public Material() {
            this(null, null, null, DEFAULT_COLOR, 0.0f, 0.0f);
        }
    }

    public record MeshData(float[] positions, float[] normals, float[] tangents, float[] biTangents,
                           float[] textCoords, int[] indices, int materialIdx) {

    }
}