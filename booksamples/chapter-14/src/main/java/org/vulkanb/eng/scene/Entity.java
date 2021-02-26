package org.vulkanb.eng.scene;

import org.joml.*;

public class Entity {

    private EntityAnimation entityAnimation;
    private String id;
    private String modelId;
    private Matrix4f modelMatrix;
    private Vector3f position;
    private Quaternionf rotation;
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

    public void setEntityAnimation(EntityAnimation entityAnimation) {
        this.entityAnimation = entityAnimation;
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

    public void setScale(float scale) {
        this.scale = scale;
        updateModelMatrix();
    }

    public final void setPosition(float x, float y, float z) {
        position.x = x;
        position.y = y;
        position.z = z;
        updateModelMatrix();
    }

    public void updateModelMatrix() {
        modelMatrix.translationRotateScale(position, rotation, scale);
    }

    public static class EntityAnimation {
        private int animationIdx;
        private int currentFrame;
        private boolean started;

        public EntityAnimation(boolean started, int animationIdx, int currentFrame) {
            this.started = started;
            this.animationIdx = animationIdx;
            this.currentFrame = currentFrame;
        }

        public int getAnimationIdx() {
            return animationIdx;
        }

        public void setAnimationIdx(int animationIdx) {
            this.animationIdx = animationIdx;
        }

        public int getCurrentFrame() {
            return currentFrame;
        }

        public void setCurrentFrame(int currentFrame) {
            this.currentFrame = currentFrame;
        }

        public boolean isStarted() {
            return started;
        }

        public void setStarted(boolean started) {
            this.started = started;
        }
    }
}