package org.vulkanb.eng.graph.shadows;

import org.joml.*;
import org.vulkanb.eng.graph.vk.GraphConstants;
import org.vulkanb.eng.scene.Scene;

import java.util.List;

public class CascadeShadow {

    private Matrix4f projViewMatrix;
    private float splitDistance;

    public CascadeShadow() {
        projViewMatrix = new Matrix4f();
    }

    // Function are derived from Vulkan examples from Sascha Willems, and licensed under the MIT License:
    // https://github.com/SaschaWillems/Vulkan/tree/master/examples/shadowmappingcascade, which are based on
    // https://johanmedestrom.wordpress.com/2016/03/18/opengl-cascaded-shadow-maps/
    public static void updateCascadeShadows(List<CascadeShadow> cascadeShadows, Scene scene) {
        Matrix4f viewMatrix = scene.getCamera().getViewMatrix();
        Matrix4f projMatrix = scene.getProjection().getProjectionMatrix();
        Vector4f lightPos = scene.getDirectionalLight().getPosition();

        float cascadeSplitLambda = 0.95f;

        float[] cascadeSplits = new float[GraphConstants.SHADOW_MAP_CASCADE_COUNT];

        float nearClip = projMatrix.perspectiveNear();
        float farClip = projMatrix.perspectiveFar();
        float clipRange = farClip - nearClip;

        float minZ = nearClip;
        float maxZ = nearClip + clipRange;

        float range = maxZ - minZ;
        float ratio = maxZ / minZ;

        // Calculate split depths based on view camera frustum
        // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            float p = (i + 1) / (float) (GraphConstants.SHADOW_MAP_CASCADE_COUNT);
            float log = (float) (minZ * java.lang.Math.pow(ratio, p));
            float uniform = minZ + range * p;
            float d = cascadeSplitLambda * (log - uniform) + uniform;
            cascadeSplits[i] = (d - nearClip) / clipRange;
        }

        // Calculate orthographic projection matrix for each cascade
        float lastSplitDist = 0.0f;
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            float splitDist = cascadeSplits[i];

            Vector3f[] frustumCorners = new Vector3f[]{
                    new Vector3f(-1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, 1.0f, 0.0f),
                    new Vector3f(1.0f, -1.0f, 0.0f),
                    new Vector3f(-1.0f, -1.0f, 0.0f),
                    new Vector3f(-1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, -1.0f, 1.0f),
                    new Vector3f(-1.0f, -1.0f, 1.0f),
            };

            // Project frustum corners into world space
            Matrix4f invCam = (new Matrix4f(projMatrix).mul(viewMatrix)).invert();
            for (int j = 0; j < 8; j++) {
                Vector4f invCorner = new Vector4f(frustumCorners[j], 1.0f).mul(invCam);
                frustumCorners[j] = new Vector3f(invCorner.x / invCorner.w, invCorner.y / invCorner.w, invCorner.z / invCorner.w);
            }

            for (int j = 0; j < 4; j++) {
                Vector3f dist = new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
                frustumCorners[j + 4] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(splitDist));
                frustumCorners[j] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(lastSplitDist));
            }

            // Get frustum center
            Vector3f frustumCenter = new Vector3f(0.0f);
            for (int j = 0; j < 8; j++) {
                frustumCenter.add(frustumCorners[j]);
            }
            frustumCenter.div(8.0f);

            float radius = 0.0f;
            for (int j = 0; j < 8; j++) {
                float distance = (new Vector3f(frustumCorners[j]).sub(frustumCenter)).length();
                radius = java.lang.Math.max(radius, distance);
            }
            radius = (float) java.lang.Math.ceil(radius * 16.0f) / 16.0f;

            Vector3f maxExtents = new Vector3f(radius);
            Vector3f minExtents = new Vector3f(maxExtents).mul(-1);

            Vector3f lightDir = (new Vector3f(lightPos.x, lightPos.y, lightPos.z).mul(-1)).normalize();
            Vector3f eye = new Vector3f(frustumCenter).sub(new Vector3f(lightDir).mul(-minExtents.z));
            Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
            Matrix4f lightViewMatrix = new Matrix4f().lookAt(eye, frustumCenter, up);
            Matrix4f lightOrthoMatrix = new Matrix4f().ortho
                    (minExtents.x, maxExtents.x, minExtents.y, maxExtents.y, 0.0f, maxExtents.z - minExtents.z, true);

            // Store split distance and matrix in cascade
            CascadeShadow cascadeShadow = cascadeShadows.get(i);
            cascadeShadow.splitDistance = (nearClip + splitDist * clipRange) * -1.0f;
            cascadeShadow.projViewMatrix = lightOrthoMatrix.mul(lightViewMatrix);

            lastSplitDist = cascadeSplits[i];
        }
    }

    public Matrix4f getProjViewMatrix() {
        return projViewMatrix;
    }

    public float getSplitDistance() {
        return splitDistance;
    }

}
