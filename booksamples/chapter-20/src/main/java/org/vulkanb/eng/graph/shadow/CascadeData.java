package org.vulkanb.eng.graph.shadow;

import org.joml.Matrix4f;

public class CascadeData {

    private final Matrix4f projViewMatrix;
    private float splitDistance;

    public CascadeData() {
        projViewMatrix = new Matrix4f();
    }

    public Matrix4f getProjViewMatrix() {
        return projViewMatrix;
    }

    public float getSplitDistance() {
        return splitDistance;
    }

    public void setProjViewMatrix(Matrix4f projViewMatrix) {
        this.projViewMatrix.set(projViewMatrix);
    }

    public void setSplitDistance(float splitDistance) {
        this.splitDistance = splitDistance;
    }
}
