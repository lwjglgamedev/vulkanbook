package org.vulkanb.eng.graph;

import org.tinylog.Logger;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;

import java.io.IOException;
import java.util.*;

import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB;

public class TextureCache {

    public static final int MAX_TEXTURES = 100;
    private final IndexedLinkedHashMap<String, Texture> textureMap;

    public TextureCache() {
        textureMap = new IndexedLinkedHashMap<>();
    }

    public Texture addTexture(VkCtx vkCtx, String id, ImageSrc srcImage, int format) {
        if (textureMap.size() > MAX_TEXTURES) {
            throw new IllegalArgumentException("Texture cache is full");
        }
        Texture texture = textureMap.get(id);
        if (texture == null) {
            texture = new Texture(vkCtx, id, srcImage, format);
            textureMap.put(id, texture);
        }
        return texture;
    }

    public Texture addTexture(VkCtx vkCtx, String id, String texturePath, int format) {
        ImageSrc srcImage = null;
        Texture result = null;
        try {
            srcImage = GraphUtils.loadImage(texturePath);
            result = addTexture(vkCtx, id, srcImage, format);
        } catch (IOException excp) {
            Logger.error("Could not load texture [{}], {}", texturePath, excp);
        } finally {
            if (srcImage != null) {
                GraphUtils.cleanImageData(srcImage);
            }
        }
        return result;
    }

    public void cleanup(VkCtx vkCtx) {
        textureMap.forEach((k, t) -> t.cleanup(vkCtx));
        textureMap.clear();
    }

    public List<Texture> getAsList() {
        return new ArrayList<>(textureMap.values());
    }

    public int getPosition(String id) {
        int result = -1;
        if (id != null) {
            result = textureMap.getIndexOf(id);
        }
        return result;
    }

    public Texture getTexture(String texturePath) {
        return textureMap.get(texturePath.trim());
    }

    public void transitionTexts(VkCtx vkCtx, CmdPool cmdPool, Queue queue) {
        Logger.debug("Recording texture transitions");
        int numTextures = textureMap.size();
        if (numTextures < MAX_TEXTURES) {
            int numPaddingTexts = MAX_TEXTURES - numTextures;
            String defaultTexturePath = EngCfg.getInstance().getDefaultTexturePath();
            for (int i = 0; i < numPaddingTexts; i++) {
                addTexture(vkCtx, UUID.randomUUID().toString(), defaultTexturePath, VK_FORMAT_R8G8B8A8_SRGB);
            }
        }
        var cmdBuf = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmdBuf.beginRecording();
        textureMap.forEach((k, v) -> v.recordTextureTransition(cmdBuf));
        cmdBuf.endRecording();
        cmdBuf.submitAndWait(vkCtx, queue);
        cmdBuf.cleanup(vkCtx, cmdPool);
        textureMap.forEach((k, v) -> v.cleanupStgBuffer(vkCtx));
        Logger.debug("Recorded texture transitions");
    }
}