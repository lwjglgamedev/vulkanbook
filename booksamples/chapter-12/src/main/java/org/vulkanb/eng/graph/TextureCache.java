package org.vulkanb.eng.graph;

import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;

import java.util.*;

public class TextureCache {

    private final IndexedLinkedHashMap<String, Texture> textureMap;

    public TextureCache() {
        textureMap = new IndexedLinkedHashMap<>();
    }

    public void cleanup() {
        textureMap.forEach((k, v) -> v.cleanup());
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

    public List<Texture> getAsList() {
        return new ArrayList<>(textureMap.values());
    }

    public int getPosition(String texturePath) {
        int result = -1;
        if (texturePath != null) {
            result = textureMap.getIndexOf(texturePath);
        }
        return result;
    }

    public Texture getTexture(String texturePath) {
        return textureMap.get(texturePath.trim());
    }
}