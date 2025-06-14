package org.vulkanb.boxes;

import com.google.gson.*;
import imgui.*;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class FontsManager {

    private static final String CONFIG_FILE = "resources/fonts/fonts.json";

    private final Map<String, ImFont> fontsMap;
    private ImFont defaultFont;

    public FontsManager() {
        fontsMap = new HashMap<>();
        Logger.debug("Loading font configuration file {}", CONFIG_FILE);
        try {
            ImGuiIO imGuiIO = ImGui.getIO();
            defaultFont = imGuiIO.getFonts().addFontDefault();

            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));

            var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            List<FontData> fontDataList = new ArrayList<>();
            fontDataList.addAll(Arrays.asList(gson.fromJson(cfgFileContents, FontData[].class)));

            for (FontData fontData : fontDataList) {
                Logger.debug("Loading font [{}]", fontData.ttfFile());
                ImFont font = imGuiIO.getFonts().addFontFromFileTTF(fontData.ttfFile(), fontData.size());
                fontsMap.put(fontData.id(), font);
            }
        } catch (IOException excp) {
            Logger.error("Error loading configuration file {}", CONFIG_FILE, excp);
        }
    }

    public ImFont getDefaultFont() {
        return defaultFont;
    }

    public ImFont getFont(String fontId) {
        ImFont font;
        if (fontsMap.containsKey(fontId)) {
            font = fontsMap.get(fontId);
        } else {
            Logger.warn("Requested unknown font {}", fontId);
            font = defaultFont;
        }
        return font;
    }

    record FontData(String id, String ttfFile, float size) {
    }
}