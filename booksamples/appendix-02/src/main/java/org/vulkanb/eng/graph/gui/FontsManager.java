package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.type.ImInt;
import org.json.*;
import org.tinylog.Logger;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;

public class FontsManager {

    private static final String CONFIG_FILE = "resources/fonts/fonts.json";

    private final Map<String, ImFont> fontsMap;
    private ImFont defaultFont;
    private Texture fontsTexture;

    public FontsManager(CommandPool commandPool, Queue queue) {
        Logger.debug("Loading font configuration file {}", CONFIG_FILE);
        fontsMap = new HashMap<>();

        try {
            ImGuiIO imGuiIO = ImGui.getIO();
            defaultFont = imGuiIO.getFonts().addFontDefault();

            String cfgFileContents = Files.readString(Path.of(CONFIG_FILE));
            JSONObject jsonObj = new JSONObject(cfgFileContents);

            JSONArray arr = jsonObj.getJSONArray("fonts");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jsonObject = arr.getJSONObject(i);
                String id = jsonObject.getString("id");
                String ttfFile = jsonObject.getString("ttfFile");
                float size = jsonObject.getFloat("size");

                ImFont font = imGuiIO.getFonts().addFontFromFileTTF(ttfFile, size);
                fontsMap.put(id, font);
            }
            imGuiIO.getFonts().build();

            ImInt texWidth = new ImInt();
            ImInt texHeight = new ImInt();
            ByteBuffer buf = imGuiIO.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
            fontsTexture = new Texture(commandPool.getDevice(), buf, texWidth.get(), texHeight.get(), VK_FORMAT_R8G8B8A8_UNORM);

            CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
            cmd.beginRecording();
            fontsTexture.recordTextureTransition(cmd);
            cmd.endRecording();
            cmd.submitAndWait(commandPool.getDevice(), queue);
            cmd.cleanup();
        } catch (IOException excp) {
            Logger.error("Error loading configuration file {}", CONFIG_FILE, excp);
        }
    }

    public void cleanup() {
        fontsTexture.cleanup();
    }

    public ImFont getDefaultFont() {
        return defaultFont;
    }

    public Texture getFontsTexture() {
        return fontsTexture;
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
}