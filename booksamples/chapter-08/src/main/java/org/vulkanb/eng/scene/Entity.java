package org.vulkanb.eng.scene;

import org.joml.*;

public class Entity {

    private String id;
    private String meshId;
    private Matrix4f modelMatrix;
    private Vector3f position;
    private Quaternionf rotation;
    private float scale;

    public Entity(String id, String meshId, Vector3f position) {
        this.id = id;
        this.meshId = meshId;
        this.position = position;
        this.scale = 1;
        this.rotation = new Quaternionf();
        this.modelMatrix = new Matrix4f();
        updateModelMatrix();
    }

    public String getId() {
        return this.id;
    }

    public String getMeshId() {
        return this.meshId;
    }

    public Matrix4f getModelMatrix() {
        return this.modelMatrix;
    }

    public Vector3f getPosition() {
        return this.position;
    }

    public Quaternionf getRotation() {
        return this.rotation;
    }

    public float getScale() {
        return this.scale;
    }

    public final void setPosition(float x, float y, float z) {
        this.position.x = x;
        this.position.y = y;
        this.position.z = z;
        this.updateModelMatrix();
    }

    public void setScale(float scale) {
        this.scale = scale;
        this.updateModelMatrix();
    }

    public void updateModelMatrix() {
        this.modelMatrix.identity().translationRotateScale(this.position, this.rotation, this.scale);
    }
}