package org.vulkanb.boxes;

import com.google.gson.*;
import org.tinylog.Logger;
import org.vulkanb.eng.scene.Scene;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LevelsLoader {

    private static final String CONFIG_FILE = "resources/maps/maps.json";

    private final List<LevelData> levelDataList;

    public LevelsLoader() {
        Logger.debug("Loading maps configuration file {}", CONFIG_FILE);

        levelDataList = new ArrayList<>();
        try {
            var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));
            levelDataList.addAll(Arrays.asList(gson.fromJson(cfgFileContents, LevelData[].class)));
        } catch (IOException excp) {
            Logger.error("Error loading configuration file {}", CONFIG_FILE, excp);
        }
    }

    public List<LevelData> getLevelDataList() {
        return levelDataList;
    }

    public GameLevel loadGameLevel(int pos, Scene scene) {
        return new GameLevel(levelDataList.get(pos), scene);
    }

    public record LevelData(String id, String file) {
    }
}
