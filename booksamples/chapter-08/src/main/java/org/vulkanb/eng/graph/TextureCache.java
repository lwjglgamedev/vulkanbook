package org.vulkanb.eng.graph;

import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;

import java.util.*;

public class TextureCache {

    private Map<String, Texture> textureMap;

    public TextureCache() {
        this.textureMap = new HashMap<>();
    }

    public synchronized void cleanUp() {
        for (Map.Entry<String, Texture> entry : textureMap.entrySet()) {
            entry.getValue().cleanUp();
        }
        this.textureMap.clear();
    }

    public synchronized Texture createTexture(CommandPool commandPool, Queue queue, String texturePath, int format) {
        String path = texturePath;
        if (texturePath == null || texturePath.trim().isEmpty()) {
            EngineProperties engProperties = EngineProperties.getInstance();
            path = engProperties.getDefaultTexturePath();
        }
        Texture texture = this.textureMap.get(path);
        if (texture == null) {
            texture = new Texture(commandPool, queue, path, format);
            this.textureMap.put(path, texture);
        }
        return texture;
    }

    public Texture getTexture(String texturePath) {
        return this.textureMap.get(texturePath.trim());
    }
}