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
        this.setTexturePath(texturePath);
        this.diffuseColor = diffuseColor;
    }

    public Vector4f getDiffuseColor() {
        return this.diffuseColor;
    }

    public String getTexturePath() {
        return this.texturePath;
    }

    public boolean hasTexture() {
        return hasTexture;
    }

    public void setDiffuseColor(Vector4f diffuseColor) {
        this.diffuseColor = diffuseColor;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
        this.hasTexture = this.texturePath != null && this.texturePath.trim().length() > 0;
    }
}