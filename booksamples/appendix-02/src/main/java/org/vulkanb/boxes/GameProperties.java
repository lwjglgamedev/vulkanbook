package org.vulkanb.boxes;

import org.tinylog.Logger;

import java.io.*;
import java.util.Properties;

public class GameProperties {
    private static final String FILENAME = "game.properties";
    private static GameProperties instance;
    private float soundGain;

    private GameProperties() {
        // Singleton
        Properties props = new Properties();

        try (InputStream stream = GameProperties.class.getResourceAsStream("/" + FILENAME)) {
            props.load(stream);
            soundGain = Float.parseFloat(props.getOrDefault("soundGain", 1.0f).toString());
        } catch (IOException excp) {
            Logger.error("Could not read [{}] properties file", FILENAME, excp);
        }
    }

    public static synchronized GameProperties getInstance() {
        if (instance == null) {
            instance = new GameProperties();
        }
        return instance;
    }

    public float getSoundGain() {
        return soundGain;
    }
}
