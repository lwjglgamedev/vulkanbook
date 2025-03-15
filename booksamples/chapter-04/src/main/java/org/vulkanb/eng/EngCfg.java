package org.vulkanb.eng;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class EngCfg {
    private static final int DEFAULT_UPS = 30;
    private static final String FILENAME = "eng.properties";
    private static EngCfg instance;
    private String physDeviceName;
    private int requestedImages;
    private int ups;
    private boolean vSync;
    private boolean vkValidate;

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

    public boolean isVkValidate() {
        return vkValidate;
    }
}
