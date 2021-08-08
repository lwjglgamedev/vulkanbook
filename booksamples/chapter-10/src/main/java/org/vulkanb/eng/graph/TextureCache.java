package org.vulkanb.eng.graph;

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
        if (texturePath == null || texturePath.trim().isEmpty()) {
            return null;
        }
        Texture texture = textureMap.get(texturePath);
        if (texture == null) {
            texture = new Texture(device, texturePath, format);
            textureMap.put(texturePath, texture);
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