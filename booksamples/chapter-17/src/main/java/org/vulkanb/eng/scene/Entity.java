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

    public boolean hasAnimation() {
        return entityAnimation != null;
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

    public static class EntityAnimation {
        private int animationIdx;
        private int currentFrame;
        private long frameStartTs;
        private boolean started;

        public EntityAnimation(boolean started, int animationIdx, int currentFrame) {
            this.started = started;
            this.animationIdx = animationIdx;
            this.currentFrame = currentFrame;
            if (started) {
                frameStartTs = System.currentTimeMillis();
            }
        }

        public int getAnimationIdx() {
            return animationIdx;
        }

        public int getCurrentFrame() {
            return currentFrame;
        }

        public long getFrameStartTs() {
            return frameStartTs;
        }

        public boolean isStarted() {
            return started;
        }

        public void setAnimationIdx(int animationIdx) {
            this.animationIdx = animationIdx;
        }

        public void setCurrentFrame(int currentFrame) {
            this.currentFrame = currentFrame;
        }

        public void setStarted(boolean started) {
            this.started = started;
            if (started) {
                frameStartTs = System.currentTimeMillis();
            }
        }
    }
}