package org.vulkanb.eng.graph.shadows;

import org.joml.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.scene.*;

import java.lang.Math;
import java.util.List;

public class CascadeShadow {

    private static final int FRUSTUM_CORNERS = 8;
    /**
     * Center of the view cuboid un world space coordinates.
     */
    private Vector3f centroid;
    private Vector3f[] frustumCorners;
    private Matrix4f lightViewMatrix;
    private Matrix4f orthoProjMatrix;
    private Matrix4f projViewMatrix;
    private Vector4f tmpVec;
    private float zFar;
    private float zNear;

    public CascadeShadow(float zNear, float zFar) {
        this.zNear = zNear;
        this.zFar = zFar;
        centroid = new Vector3f();
        lightViewMatrix = new Matrix4f();
        orthoProjMatrix = new Matrix4f();
        frustumCorners = new Vector3f[FRUSTUM_CORNERS];
        for (int i = 0; i < FRUSTUM_CORNERS; i++) {
            frustumCorners[i] = new Vector3f();
        }
        tmpVec = new Vector4f();
        projViewMatrix = new Matrix4f();
    }

    public static void updateCascadeShadows(int width, int height, List<CascadeShadow> cascadeShadows, Scene scene) {
        int size = cascadeShadows.size();
        for (int i = 0; i < size; i++) {
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            cascadeShadow.updateCascadeShadow(width, height, scene.getCamera().getViewMatrix(), scene.getDirectionalLight());
        }
    }

    public Matrix4f getProjViewMatrix() {
        return projViewMatrix;
    }

    public float getZFar() {
        return zFar;
    }

    public void updateCascadeShadow(int width, int height, Matrix4f viewMatrix, Light light) {
        // Build projection view matrix for this cascade
        float aspectRatio = (float) width / (float) height;
        float fov = EngineProperties.getInstance().getFov();
        Matrix4f perspectiveViewMatrix = new Matrix4f();
        perspectiveViewMatrix.setPerspective(fov, aspectRatio, zNear, zFar);
        perspectiveViewMatrix.mul(viewMatrix);

        // Calculate frustum corners in world space
        float maxZ = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE;
        for (int i = 0; i < FRUSTUM_CORNERS; i++) {
            Vector3f corner = frustumCorners[i];
            corner.set(0, 0, 0);
            perspectiveViewMatrix.frustumCorner(i, corner);
            centroid.add(corner);
            centroid.div(8.0f);
            minZ = Math.min(minZ, corner.z);
            maxZ = Math.max(maxZ, corner.z);
        }

        // Go back from the centroid up to max.z - min.z in the direction of light
        Vector3f lightDirection = new Vector3f(light.getPosition().x(), light.getPosition().y(), light.getPosition().z());
        Vector3f lightPosInc = new Vector3f().set(lightDirection);
        float distance = maxZ - minZ;
        lightPosInc.mul(distance);
        Vector3f lightPosition = new Vector3f();
        lightPosition.set(centroid);
        lightPosition.add(lightPosInc);

        updateLightViewMatrix(lightDirection, lightPosition);

        updateLightProjectionMatrix();

        projViewMatrix = new Matrix4f(orthoProjMatrix).mul(lightViewMatrix);
    }

    private void updateLightProjectionMatrix() {
        // Now calculate frustum dimensions in light space
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MIN_VALUE;
        for (int i = 0; i < FRUSTUM_CORNERS; i++) {
            Vector3f corner = frustumCorners[i];
            tmpVec.set(corner, 1);
            tmpVec.mul(lightViewMatrix);
            minX = Math.min(tmpVec.x, minX);
            maxX = Math.max(tmpVec.x, maxX);
            minY = Math.min(tmpVec.y, minY);
            maxY = Math.max(tmpVec.y, maxY);
            minZ = Math.min(tmpVec.z, minZ);
            maxZ = Math.max(tmpVec.z, maxZ);
        }
        float distz = maxZ - minZ;

        orthoProjMatrix.setOrtho(minX, maxX, minY, maxY, 0, distz, true);
    }

    private void updateLightViewMatrix(Vector3f lightDirection, Vector3f lightPosition) {
        float lightAngleX = (float) Math.toDegrees(Math.acos(lightDirection.z));
        float lightAngleY = (float) Math.toDegrees(Math.asin(lightDirection.x));

        lightViewMatrix.rotationX((float) Math.toRadians(lightAngleX))
                .rotateY((float) Math.toRadians(lightAngleY))
                .translate(-lightPosition.x, -lightPosition.y, -lightPosition.z);
    }
}
