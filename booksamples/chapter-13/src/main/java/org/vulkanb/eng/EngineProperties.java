package org.vulkanb.eng;

import org.apache.logging.log4j.*;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final float DEFAULT_FOV = 60.0f;
    private static final int DEFAULT_MAX_MATERIALS = 500;
    private static final int DEFAULT_REQUESTED_IMAGES = 3;
    private static final float DEFAULT_SHADOW_Z_FAR = 50.0f;
    private static final float DEFAULT_SHADOW_Z_NEAR = 1.0f;
    private static final int DEFAULT_UPS = 30;
    private static final float DEFAULT_Z_FAR = 100.f;
    private static final float DEFAULT_Z_NEAR = 1.0f;
    private static final String FILENAME = "eng.properties";
    private static final Logger LOGGER = LogManager.getLogger();
    private static EngineProperties instance;
    private String defaultTexturePath;
    private float fov;
    private int maxMaterials;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
    private float shadowZFar;
    private float shadowZNear;
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
            physDeviceName = props.getProperty("physdeviceName");
            requestedImages = Integer.parseInt(props.getOrDefault("requestedImages", DEFAULT_REQUESTED_IMAGES).toString());
            vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
            shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
            fov = (float) Math.toRadians(Float.parseFloat(props.getOrDefault("fov", DEFAULT_FOV).toString()));
            zNear = Float.parseFloat(props.getOrDefault("zNear", DEFAULT_Z_NEAR).toString());
            zFar = Float.parseFloat(props.getOrDefault("zFar", DEFAULT_Z_FAR).toString());
            defaultTexturePath = props.getProperty("defaultTexturePath");
            maxMaterials = Integer.parseInt(props.getOrDefault("maxMaterials", DEFAULT_MAX_MATERIALS).toString());
            shadowZNear = Float.parseFloat(props.getOrDefault("shadowZNear", DEFAULT_SHADOW_Z_NEAR).toString());
            shadowZFar = Float.parseFloat(props.getOrDefault("shadowZFar", DEFAULT_SHADOW_Z_FAR).toString());
        } catch (IOException excp) {
            LOGGER.error("Could not read [{}] properties file", FILENAME, excp);
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

    public int getMaxMaterials() {
        return maxMaterials;
    }

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
    }

    public float getShadowZFar() {
        return shadowZFar;
    }

    public float getShadowZNear() {
        return shadowZNear;
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
