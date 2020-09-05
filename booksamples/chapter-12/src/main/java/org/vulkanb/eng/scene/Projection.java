package org.vulkanb.eng.scene;

import org.joml.Matrix4f;
import org.vulkanb.eng.EngineProperties;

public class Projection {

    private Matrix4f projectionMatrix;

    public Projection() {
        projectionMatrix = new Matrix4f();
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public void resize(int width, int height) {
        EngineProperties engProps = EngineProperties.getInstance();
        projectionMatrix.identity();
        projectionMatrix.perspective(engProps.getFov(), (float) width / (float) height,
                engProps.getZNear(), engProps.getZFar(), true);
    }
}
