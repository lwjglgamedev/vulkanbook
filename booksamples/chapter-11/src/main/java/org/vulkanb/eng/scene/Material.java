package org.vulkanb.eng.scene;

import org.joml.Vector4f;

public class Material {

    public static final Vector4f DEFAULT_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private Vector4f diffuseColor;
    private boolean hasMetalRoughMap;
    private boolean hasNormalMap;
    private boolean hasTexture;
    private String metalRoughMap;
    private float metallicFactor;
    private String normalMapPath;
    private float roughnessFactor;
    private String texturePath;

    public Material() {
        this(null, null, null, DEFAULT_COLOR, 0.0f, 0.0f);
    }

    public Material(String texturePath, String normalMapPath, String metalRoughMap, Vector4f diffuseColor,
                    float roughnessFactor, float metallicFactor) {
        setTexturePath(texturePath);
        setNormalMapPath(normalMapPath);
        setMetalRoughMap(metalRoughMap);
        this.roughnessFactor = roughnessFactor;
        this.metallicFactor = metallicFactor;
        this.diffuseColor = diffuseColor;
    }

    public Vector4f getDiffuseColor() {
        return diffuseColor;
    }

    public String getMetalRoughPath() {
        return metalRoughMap;
    }

    public float getMetallicFactor() {
        return metallicFactor;
    }

    public String getNormalMapPath() {
        return normalMapPath;
    }

    public float getRoughnessFactor() {
        return roughnessFactor;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public boolean hasMetalRoughMap() {
        return hasMetalRoughMap;
    }

    public boolean hasNormalMap() {
        return hasNormalMap;
    }

    public boolean hasTexture() {
        return hasTexture;
    }

    public void setDiffuseColor(Vector4f diffuseColor) {
        this.diffuseColor = diffuseColor;
    }

    public void setMetalRoughMap(String metalRoughMap) {
        this.metalRoughMap = metalRoughMap;
        hasMetalRoughMap = this.metalRoughMap != null && this.metalRoughMap.trim().length() > 0;
    }

    public void setNormalMapPath(String normalMapPath) {
        this.normalMapPath = normalMapPath;
        hasNormalMap = this.normalMapPath != null && this.normalMapPath.trim().length() > 0;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
        hasTexture = this.texturePath != null && this.texturePath.trim().length() > 0;
    }
}