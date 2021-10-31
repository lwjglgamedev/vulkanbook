package org.vulkanb.eng;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class EngineProperties {
    private static final int DEFAULT_UPS = 30;
    private static final String FILENAME = "eng.properties";
    private static EngineProperties instance;
    private int ups;
    private boolean validate;

    private EngineProperties() {
        // Singleton
        Properties props = new Properties();

        try (InputStream stream = EngineProperties.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
            validate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
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

    public int getUps() {
        return ups;
    }

    public boolean isValidate() {
        return validate;
    }
}
