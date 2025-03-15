package org.vulkanb.eng.model;

import com.google.gson.*;
import org.tinylog.Logger;

import java.nio.file.*;
import java.util.*;

public class ModelLoader {

    private ModelLoader() {
        // Utility class
    }

    public static List<MaterialData> loadMaterials(String path) {
        Logger.debug("Loading materials from [{}]", path);
        var result = new ArrayList<MaterialData>();
        try {
            var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            String content = new String(Files.readAllBytes(Paths.get(path)));
            result.addAll(Arrays.asList(gson.fromJson(content, MaterialData[].class)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static ModelData loadModel(String path) {
        Logger.debug("Loading model from [{}]", path);
        ModelData result;
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            result = gson.fromJson(content, ModelData.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
