package org.vulkanb.eng.graph.shadow;

import org.joml.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.scene.*;

import java.util.List;

public class ShadowUtils {

    private static final float LAMBDA = 0.95f;
    private static final Vector3f UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final Vector3f UP_ALT = new Vector3f(0.0f, 0.0f, 1.0f);

    private ShadowUtils() {
        // Utility class
    }

    // Function are derived from Vulkan examples from Sascha Willems, and licensed under the MIT License:
    // https://github.com/SaschaWillems/Vulkan/tree/master/examples/shadowmappingcascade, which are based on
    // https://johanmedestrom.wordpress.com/2016/03/18/opengl-cascaded-shadow-maps/
    // combined with this source: https://github.com/TheRealMJP/Shadows
    public static void updateCascadeShadows(CascadeShadows cascadeShadows, Scene scene) {
        Camera camera = scene.getCamera();
        Matrix4f viewMatrix = camera.getViewMatrix();
        Projection projection = scene.getProjection();
        Matrix4f projMatrix = projection.getProjectionMatrix();
        Light[] lights = scene.getLights();
        int numLights = lights.length;
        Light dirLight = null;
        for (int i = 0; i < numLights; i++) {
            if (lights[i].getPosition().w == 0) {
                dirLight = lights[i];
                break;
            }
        }
        if (dirLight == null) {
            throw new RuntimeException("Could not find directional light");
        }
        Vector4f lightPos = dirLight.getPosition();

        float[] cascadeSplits = new float[Scene.SHADOW_MAP_CASCADE_COUNT];

        float nearClip = projection.getZNear();
        float farClip = projection.getZFar();
        float clipRange = farClip - nearClip;

        float minZ = nearClip;
        float maxZ = nearClip + clipRange;

        float range = maxZ - minZ;
        float ratio = maxZ / minZ;

        List<CascadeData> cascadeDataList = cascadeShadows.getCascadeData();
        int numCascades = cascadeDataList.size();

        // Calculate split depths based on view camera frustum
        // Based on method presented in https://developer.nvidia.com/gpugems/GPUGems3/gpugems3_ch10.html
        for (int i = 0; i < numCascades; i++) {
            float p = (i + 1) / (float) (Scene.SHADOW_MAP_CASCADE_COUNT);
            float log = (float) (minZ * java.lang.Math.pow(ratio, p));
            float uniform = minZ + range * p;
            float d = LAMBDA * (log - uniform) + uniform;
            cascadeSplits[i] = (d - nearClip) / clipRange;
        }

        // Calculate orthographic projection matrix for each cascade
        float lastSplitDist = 0.0f;
        for (int i = 0; i < numCascades; i++) {
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
            var invCam = (new Matrix4f(projMatrix).mul(viewMatrix)).invert();
            for (int j = 0; j < 8; j++) {
                Vector4f invCorner = new Vector4f(frustumCorners[j], 1.0f).mul(invCam);
                frustumCorners[j] = new Vector3f(invCorner.x, invCorner.y, invCorner.z).div(invCorner.w);
            }

            for (int j = 0; j < 4; j++) {
                var dist = new Vector3f(frustumCorners[j + 4]).sub(frustumCorners[j]);
                frustumCorners[j + 4] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(splitDist));
                frustumCorners[j] = new Vector3f(frustumCorners[j]).add(new Vector3f(dist).mul(lastSplitDist));
            }

            // Get frustum center
            var frustumCenter = new Vector3f(0.0f);
            for (int j = 0; j < 8; j++) {
                frustumCenter.add(frustumCorners[j]);
            }
            frustumCenter.div(8.0f);

            var up = UP;
            float sphereRadius = 0.0f;
            for (int j = 0; j < 8; j++) {
                float dist = new Vector3f(frustumCorners[j]).sub(frustumCenter).length();
                sphereRadius = java.lang.Math.max(sphereRadius, dist);
            }
            sphereRadius = (float) java.lang.Math.ceil(sphereRadius * 16.0f) / 16.0f;

            var maxExtents = new Vector3f(sphereRadius, sphereRadius, sphereRadius);
            var minExtents = new Vector3f(maxExtents).mul(-1.0f);

            var lightDir = new Vector3f(lightPos.x, lightPos.y, lightPos.z);
            // Get position of the shadow camera
            var shadowCameraPos = new Vector3f(frustumCenter).add(lightDir.mul(minExtents.z));

            float dot = java.lang.Math.abs(new Vector3f(lightPos.x, lightPos.y, lightPos.z).dot(up));
            if (dot == 1.0f) {
                up = UP_ALT;
            }

            var lightViewMatrix = new Matrix4f().lookAt(shadowCameraPos, frustumCenter, up);
            var lightOrthoMatrix = new Matrix4f().ortho
                    (minExtents.x, maxExtents.x, minExtents.y, maxExtents.y, 0.0f, maxExtents.z - minExtents.z, true);

            // Stabilize shadow
            int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
            Vector4f shadowOrigin = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);
            lightViewMatrix.transform(shadowOrigin);
            shadowOrigin.mul(shadowMapSize / 2.0f);

            Vector4f roundedOrigin = new Vector4f(shadowOrigin).round();
            Vector4f roundOffset = roundedOrigin.sub(shadowOrigin);
            roundOffset.mul(2.0f / shadowMapSize);
            roundOffset.z = 0.0f;
            roundOffset.w = 0.0f;

            lightOrthoMatrix.m30(lightOrthoMatrix.m30() + roundOffset.x);
            lightOrthoMatrix.m31(lightOrthoMatrix.m31() + roundOffset.y);
            lightOrthoMatrix.m32(lightOrthoMatrix.m32() + roundOffset.z);
            lightOrthoMatrix.m33(lightOrthoMatrix.m33() + roundOffset.w);

            // Store split distance and matrix in cascade
            CascadeData cascadeData = cascadeDataList.get(i);
            cascadeData.setSplitDistance((nearClip + splitDist * clipRange) * -1.0f);
            cascadeData.setProjViewMatrix(lightOrthoMatrix.mul(lightViewMatrix));

            lastSplitDist = cascadeSplits[i];
        }
    }
}
