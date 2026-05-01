package org.vulkanb.boxes;

import com.google.gson.*;
import imgui.*;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class FontsManager {

    private static final String CONFIG_FILE = "resources/fonts/fonts.json";
    private static final int DEFAULT_SIZE = 10;

    private final Map<String, Font> fontsMap;
    private Font defaultFont;

    public FontsManager() {
        fontsMap = new HashMap<>();
        Logger.debug("Loading font configuration file {}", CONFIG_FILE);
        try {
            ImGuiIO imGuiIO = ImGui.getIO();
            defaultFont = new Font(imGuiIO.getFonts().addFontDefault(), DEFAULT_SIZE);

            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));

            var gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            List<FontData> fontDataList = new ArrayList<>();
            fontDataList.addAll(Arrays.asList(gson.fromJson(cfgFileContents, FontData[].class)));

            for (FontData fontData : fontDataList) {
                Logger.debug("Loading font [{}]", fontData.ttfFile());
                ImFont imFont = imGuiIO.getFonts().addFontFromFileTTF(fontData.ttfFile(), fontData.size());
                fontsMap.put(fontData.id(), new Font(imFont, (int) fontData.size));
            }
        } catch (IOException excp) {
            Logger.error("Error loading configuration file {}", CONFIG_FILE, excp);
        }
    }

    public Font getDefaultFont() {
        return defaultFont;
    }

    public Font getFont(String fontId) {
        Font font;
        if (fontsMap.containsKey(fontId)) {
            font = fontsMap.get(fontId);
        } else {
            Logger.warn("Requested unknown font {}", fontId);
            font = defaultFont;
        }
        return font;
    }

    public record Font(ImFont imFont, int size) {
    }

    record FontData(String id, String ttfFile, float size) {
    }
}