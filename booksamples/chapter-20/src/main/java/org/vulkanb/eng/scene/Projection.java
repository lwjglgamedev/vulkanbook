package org.vulkanb.eng.scene;

import org.joml.Matrix4f;

public class Projection {

    private final float fov;
    private final Matrix4f projectionMatrix;
    private final float zFar;
    private final float zNear;

    public Projection(float fov, float zNear, float zFar, int width, int height) {
        this.fov = fov;
        this.zNear = zNear;
        this.zFar = zFar;
        projectionMatrix = new Matrix4f();
        resize(width, height);
    }

    public float getFov() {
        return fov;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public float getZFar() {
        return zFar;
    }

    public float getZNear() {
        return zNear;
    }

    public void resize(int width, int height) {
        projectionMatrix.identity();
        projectionMatrix.perspective(fov, (float) width / (float) height, zNear, zFar, true);
        // Flip y coordinates
        projectionMatrix.m11(-projectionMatrix.m11());
    }
}
