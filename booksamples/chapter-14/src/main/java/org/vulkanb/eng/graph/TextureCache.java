package org.vulkanb.eng.graph;

import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.util.*;

public class TextureCache {

    private final Map<String, Texture> textureMap;

    public TextureCache() {
        textureMap = new HashMap<>();
    }

    public void cleanup() {
        for (Map.Entry<String, Texture> entry : textureMap.entrySet()) {
            entry.getValue().cleanup();
        }
        textureMap.clear();
    }

    public Texture createTexture(Device device, String texturePath, int format) {
        String path = texturePath;
        if (texturePath == null || texturePath.trim().isEmpty()) {
            EngineProperties engProperties = EngineProperties.getInstance();
            path = engProperties.getDefaultTexturePath();
        }
        Texture texture = textureMap.get(path);
        if (texture == null) {
            texture = new Texture(device, path, format);
            textureMap.put(path, texture);
        }
        return texture;
    }

    public Texture getTexture(String texturePath) {
        return textureMap.get(texturePath.trim());
    }
}