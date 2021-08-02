package org.vulkanb.eng;

import org.apache.logging.log4j.*;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final int DEFAULT_REQUESTED_IMAGES = 3;
    private static final int DEFAULT_UPS = 30;
    private static final String FILENAME = "eng.properties";
    private static final Logger LOGGER = LogManager.getLogger();
    private static EngineProperties instance;
    private String physDeviceName;
    private int requestedImages;
    private boolean shaderRecompilation;
    private int ups;
    private boolean vSync;
    private boolean validate;

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

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
    }

    public int getUps() {
        return ups;
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
