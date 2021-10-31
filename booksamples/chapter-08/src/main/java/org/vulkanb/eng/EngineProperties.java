package org.vulkanb.eng;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final float DEFAULT_FOV = 60.0f;
    private static final int DEFAULT_REQUESTED_IMAGES = 3;
    private static final int DEFAULT_UPS = 30;
    private static final float DEFAULT_Z_FAR = 100.f;
    private static final float DEFAULT_Z_NEAR = 1.0f;
    private static final String FILENAME = "eng.properties";
    private static EngineProperties instance;
    private String defaultTexturePath;
    private float fov;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
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

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
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

    public boolean isValidate() {
        return validate;
    }

    public boolean isvSync() {
        return vSync;
    }
}
