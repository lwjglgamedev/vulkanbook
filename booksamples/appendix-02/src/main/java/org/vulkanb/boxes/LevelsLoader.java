package org.vulkanb.boxes;

import org.json.*;
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
            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));
            JSONObject jsonObj = new JSONObject(cfgFileContents);

            JSONArray arr = jsonObj.getJSONArray("maps");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jsonObject = arr.getJSONObject(i);
                String id = jsonObject.getString("id");
                String file = jsonObject.getString("file");
                levelDataList.add(new LevelData(id, file));
            }
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
