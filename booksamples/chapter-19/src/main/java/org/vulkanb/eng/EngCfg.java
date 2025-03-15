package org.vulkanb.eng;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class EngCfg {
    private static final int DEFAULT_UPS = 30;
    private static final String FILENAME = "eng.properties";
    private static EngCfg instance;

    private boolean debugShaders;
    private String defaultTexturePath;
    private float fov;
    private boolean fxaa;
    private int maxDescs;
    private int maxJointsMatricesLists;
    private int maxMaterials;
    private int maxStorageBuffers;
    private int maxTextures;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
    private float shadowBias;
    private boolean shadowDebug;
    private int shadowMapSize;
    private boolean shadowPcf;
    private int ups;
    private boolean vSync;
    private boolean vkValidate;
    private float zFar;
    private float zNear;

    private EngCfg() {
        // Singleton
        var props = new Properties();

        try (InputStream stream = EngCfg.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
            vkValidate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
            physDeviceName = props.getProperty("physDeviceName");
            requestedImages = Integer.parseInt(props.getOrDefault("requestedImages", 3).toString());
            vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
            shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
            debugShaders = Boolean.parseBoolean(props.getOrDefault("debugShaders", false).toString());
            fov = (float) Math.toRadians(Float.parseFloat(props.getOrDefault("fov", 60.0f).toString()));
            zNear = Float.parseFloat(props.getOrDefault("zNear", 1.0f).toString());
            zFar = Float.parseFloat(props.getOrDefault("zFar", 100.f).toString());
            maxDescs = Integer.parseInt(props.getOrDefault("maxDescs", 1000).toString());
            defaultTexturePath = props.getProperty("defaultTexturePath");
            maxMaterials = Integer.parseInt(props.getOrDefault("maxMaterials", 500).toString());
            fxaa = Boolean.parseBoolean(props.getOrDefault("fxaa", true).toString());
            shadowPcf = Boolean.parseBoolean(props.getOrDefault("shadowPcf", false).toString());
            shadowBias = Float.parseFloat(props.getOrDefault("shadowBias", 0.00005f).toString());
            shadowMapSize = Integer.parseInt(props.getOrDefault("shadowMapSize", 2048).toString());
            shadowDebug = Boolean.parseBoolean(props.getOrDefault("shadowDebug", false).toString());
            maxStorageBuffers = Integer.parseInt(props.getOrDefault("maxStorageBuffers", 100).toString());
            maxJointsMatricesLists = Integer.parseInt(props.getOrDefault("maxJointsMatricesLists", 100).toString());
            maxTextures = Integer.parseInt(props.getOrDefault("maxTextures", 100).toString());
        } catch (IOException excp) {
            Logger.error("Could not read [{}] properties file", FILENAME, excp);
        }
    }

    public static synchronized EngCfg getInstance() {
        if (instance == null) {
            instance = new EngCfg();
        }
        return instance;
    }

    public String getDefaultTexturePath() {
        return defaultTexturePath;
    }

    public float getFov() {
        return fov;
    }

    public int getMaxDescs() {
        return maxDescs;
    }

    public int getMaxJointsMatricesLists() {
        return maxJointsMatricesLists;
    }

    public int getMaxMaterials() {
        return maxMaterials;
    }

    public int getMaxStorageBuffers() {
        return maxStorageBuffers;
    }

    public int getMaxTextures() {
        return maxTextures;
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

    public boolean getVSync() {
        return vSync;
    }

    public float getZFar() {
        return zFar;
    }

    public float getZNear() {
        return zNear;
    }

    public boolean isDebugShaders() {
        return debugShaders;
    }

    public boolean isFxaa() {
        return fxaa;
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

    public boolean isVkValidate() {
        return vkValidate;
    }

    public void setMaxJointsMatricesLists(int maxJointsMatricesLists) {
        this.maxJointsMatricesLists = maxJointsMatricesLists;
    }

    public void setMaxStorageBuffers(int maxStorageBuffers) {
        this.maxStorageBuffers = maxStorageBuffers;
    }
}
