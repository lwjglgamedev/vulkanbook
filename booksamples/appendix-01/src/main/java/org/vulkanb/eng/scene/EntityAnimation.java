package org.vulkanb.eng.scene;

public class EntityAnimation {
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