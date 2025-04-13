package org.vulkanb.eng.scene;

import org.joml.*;

public class Entity {

    private final String id;
    private final String modelId;
    private final Matrix4f modelMatrix;
    private final Vector3f position;
    private final Quaternionf rotation;
    private EntityAnimation entityAnimation;
    private float scale;

    public Entity(String id, String modelId, Vector3f position) {
        this.id = id;
        this.modelId = modelId;
        this.position = position;
        scale = 1;
        rotation = new Quaternionf();
        modelMatrix = new Matrix4f();
        updateModelMatrix();
    }

    public EntityAnimation getEntityAnimation() {
        return entityAnimation;
    }

    public String getId() {
        return id;
    }

    public String getModelId() {
        return modelId;
    }

    public Matrix4f getModelMatrix() {
        return modelMatrix;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public float getScale() {
        return scale;
    }

    public void resetRotation() {
        rotation.x = 0.0f;
        rotation.y = 0.0f;
        rotation.z = 0.0f;
        rotation.w = 1.0f;
    }

    public void setEntityAnimation(EntityAnimation entityAnimation) {
        this.entityAnimation = entityAnimation;
    }

    public final void setPosition(float x, float y, float z) {
        position.x = x;
        position.y = y;
        position.z = z;
        updateModelMatrix();
    }

    public void setScale(float scale) {
        this.scale = scale;
        updateModelMatrix();
    }

    public void updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale);
    }
}