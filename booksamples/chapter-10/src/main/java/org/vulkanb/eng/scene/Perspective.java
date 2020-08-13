package org.vulkanb.eng.scene;

import org.joml.Matrix4f;
import org.vulkanb.eng.EngineProperties;

public class Perspective {

    private Matrix4f invPerspectiveMatrix;
    private Matrix4f perspectiveMatrix;

    public Perspective() {
        perspectiveMatrix = new Matrix4f();
        invPerspectiveMatrix = new Matrix4f();
    }

    public Matrix4f getInvPerspectiveMatrix() {
        return invPerspectiveMatrix;
    }

    public Matrix4f getPerspectiveMatrix() {
        return perspectiveMatrix;
    }

    public void resize(int width, int height) {
        EngineProperties engProps = EngineProperties.getInstance();
        perspectiveMatrix.identity();
        perspectiveMatrix.perspective(engProps.getFov(), (float) width / (float) height,
                engProps.getZNear(), engProps.getZFar(), true);
        perspectiveMatrix.invert(invPerspectiveMatrix);
    }
}
