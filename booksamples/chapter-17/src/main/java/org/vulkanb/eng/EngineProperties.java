package org.vulkanb.eng;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final float DEFAULT_FOV = 60.0f;
    private static final int DEFAULT_JOINT_MATRICES_BUF = 2000000;
    private static final int DEFAULT_MAX_ANIM_WEIGHTS_BUF = 100000;
    private static final int DEFAULT_MAX_INDICES_BUF = 5000000;
    private static final int DEFAULT_MAX_JOINTS_MATRICES_LISTS = 100;
    private static final int DEFAULT_MAX_MATERIALS = 500;
    private static final int DEFAULT_MAX_VERTICES_BUF = 20000000;
    private static final int DEFAULT_REQUESTED_IMAGES = 3;
    private static final float DEFAULT_SHADOW_BIAS = 0.00005f;
    private static final int DEFAULT_SHADOW_MAP_SIZE = 2048;
    private static final int DEFAULT_UPS = 30;
    private static final float DEFAULT_Z_FAR = 100.f;
    private static final float DEFAULT_Z_NEAR = 1.0f;
    private static final String FILENAME = "eng.properties";
    private static EngineProperties instance;
    private String defaultTexturePath;
    private float fov;
    private int maxAnimWeightsBuffer;
    private int maxIndicesBuffer;
    private int maxJointMatricesBuffer;
    private int maxJointsMatricesLists;
    private int maxMaterials;
    private int maxTextures;
    private int maxVerticesBuffer;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
    private float shadowBias;
    private boolean shadowDebug;
    private int shadowMapSize;
    private boolean shadowPcf;
    private int ups;
    private boolean vSync;
    private boolean validate;
    private float zFar;
    private float zNear;

    private EngineProperties() {
        // Singleton
        Properties props = new Properties();

        try (InputStream stream = EngineProperties.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
            validate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
            physDeviceName = props.getProperty("physDeviceName");
            requestedImages = Integer.parseInt(props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString());
            vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
            shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
            fov = (float) Math.toRadians(Float.parseFloat(props.getOrDefault("fov", DEFAULT_FOV).toString()));
            zNear = Float.parseFloat(props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString());
            zFar = Float.parseFloat(props.getOrDefault("zFar", DEFAULT_Z_FAR).toString());
            defaultTexturePath = props.getProperty("defaultTexturePath");
            maxMaterials = Integer.parseInt(props.getOrDefault("maxMaterials", DEFAULT_MAX_MATERIALS).toString());
            shadowPcf = Boolean.parseBoolean(props.getOrDefault("shadowPcf", false).toString());
            shadowBias = Float.parseFloat(props.getOrDefault("shadowBias", DEFAULT_SHADOW_BIAS).toString());
            shadowMapSize = Integer.parseInt(props.getOrDefault("shadowMapSize", DEFAULT_SHADOW_MAP_SIZE).toString());
            shadowDebug = Boolean.parseBoolean(props.getOrDefault("shadowDebug", false).toString());
            maxTextures = maxMaterials * 3;
            maxVerticesBuffer = Integer.parseInt(props.getOrDefault("maxVerticesBuffer", DEFAULT_MAX_VERTICES_BUF).toString());
            maxIndicesBuffer = Integer.parseInt(props.getOrDefault("maxIndicesBuffer", DEFAULT_MAX_INDICES_BUF).toString());
            maxAnimWeightsBuffer = Integer.parseInt(props.getOrDefault("maxAnimWeightsBuffer", DEFAULT_MAX_ANIM_WEIGHTS_BUF).toString());
            maxJointMatricesBuffer = Integer.parseInt(props.getOrDefault("maxJointMatricesBuffer", DEFAULT_JOINT_MATRICES_BUF).toString());
            maxJointsMatricesLists = Integer.parseInt(props.getOrDefault("maxJointsMatricesLists", DEFAULT_MAX_JOINTS_MATRICES_LISTS).toString());
        } catch (IOException excp) {
            Logger.error("Could not read [{}] properties file", FILENAME, excp);
        }
    }

    public static synchronized EngineProperties getInstance() {
        if (instance == null) {
            instance = new EngineProperties();
        }
        return instance;
    }

    public String getDefaultTexturePath() {
        return defaultTexturePath;
    }

    public float getFov() {
        return fov;
    }

    public int getMaxAnimWeightsBuffer() {
        return maxAnimWeightsBuffer;
    }

    public int getMaxIndicesBuffer() {
        return maxIndicesBuffer;
    }

    public int getMaxJointMatricesBuffer() {
        return maxJointMatricesBuffer;
    }

    public int getMaxJointsMatricesLists() {
        return maxJointsMatricesLists;
    }

    public int getMaxMaterials() {
        return maxMaterials;
    }

    public int getMaxTextures() {
        return maxTextures;
    }

    public int getMaxVerticesBuffer() {
        return maxVerticesBuffer;
    }

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
    }

    public float getShadowBias() {
        return shadowBias;
    }

    public int getShadowMapSize() {
        return shadowMapSize;
    }

    public int getUps() {
        return ups;
    }

    public float getZFar() {
        return zFar;
    }

    public float getZNear() {
        return zNear;
    }

    public boolean isShaderRecompilation() {
        return shaderRecompilation;
    }

    public boolean isShadowDebug() {
        return shadowDebug;
    }

    public boolean isShadowPcf() {
        return shadowPcf;
    }

    public boolean isValidate() {
        return validate;
    }

    public boolean isvSync() {
        return vSync;
    }
}
