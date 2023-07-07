package org.vulkanb.eng.scene;

import org.joml.*;

import java.util.List;

public class ModelData {
    private List<AnimMeshData> animMeshDataList;
    private List<Animation> animationsList;
    private List<Material> materialList;
    private List<MeshData> meshDataList;
    private String modelId;

    public ModelData(String modelId, List<MeshData> meshDataList, List<Material> materialList) {
        this.modelId = modelId;
        this.meshDataList = meshDataList;
        this.materialList = materialList;
    }

    public List<AnimMeshData> getAnimMeshDataList() {
        return animMeshDataList;
    }

    public List<Animation> getAnimationsList() {
        return animationsList;
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

    public boolean hasAnimations() {
        return animationsList != null && !animationsList.isEmpty();
    }

    public void setAnimMeshDataList(List<AnimMeshData> animMeshDataList) {
        this.animMeshDataList = animMeshDataList;
    }

    public void setAnimationsList(List<Animation> animationsList) {
        this.animationsList = animationsList;
    }

    public record AnimMeshData(float[] weights, int[] boneIds) {
    }

    public record AnimatedFrame(Matrix4f[] jointMatrices) {
    }

    public record Animation(String name, float frameMillis, List<AnimatedFrame> frames) {
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