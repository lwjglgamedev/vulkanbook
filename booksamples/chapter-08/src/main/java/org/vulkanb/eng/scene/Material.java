package org.vulkanb.eng.scene;

import org.joml.Vector4f;

public class Material {

    public static final Vector4f DEFAULT_COLOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private Vector4f diffuseColor;
    private boolean hasTexture;
    private String texturePath;

    public Material() {
        this(null, DEFAULT_COLOR);
    }

    public Material(String texturePath, Vector4f diffuseColor) {
        setTexturePath(texturePath);
        this.diffuseColor = diffuseColor;
    }

    public Vector4f getDiffuseColor() {
        return diffuseColor;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public boolean hasTexture() {
        return hasTexture;
    }

    public void setDiffuseColor(Vector4f diffuseColor) {
        this.diffuseColor = diffuseColor;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
        hasTexture = this.texturePath != null && this.texturePath.trim().length() > 0;
    }
}