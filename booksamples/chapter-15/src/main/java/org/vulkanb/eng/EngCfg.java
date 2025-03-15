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
    private int maxMaterials;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
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

    public int getMaxMaterials() {
        return maxMaterials;
    }

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
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

    public boolean isVkValidate() {
        return vkValidate;
    }
}
