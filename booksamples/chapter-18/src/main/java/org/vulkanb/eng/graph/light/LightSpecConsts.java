package org.vulkanb.eng.graph.light;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.VkUtils;
import org.vulkanb.eng.scene.Scene;

import java.nio.ByteBuffer;

public class LightSpecConsts {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public LightSpecConsts() {
        var engCfg = EngCfg.getInstance();
        data = MemoryUtil.memAlloc(VkUtils.INT_SIZE * 2);
        data.putInt(Scene.SHADOW_MAP_CASCADE_COUNT);
        data.putInt(engCfg.isShadowDebug() ? 1 : 0);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(2);
        int offset = 0;
        int pos = 0;
        int size = VkUtils.INT_SIZE;
        specEntryMap.get(pos)
                .constantID(pos)
                .size(size)
                .offset(offset);
        offset += size;
        pos++;

        size = VkUtils.INT_SIZE;
        specEntryMap.get(pos)
                .constantID(pos)
                .size(size)
                .offset(offset);

        specInfo = VkSpecializationInfo.calloc();
        specInfo.pData(data)
                .pMapEntries(specEntryMap);
    }

    public void cleanup() {
        MemoryUtil.memFree(specEntryMap);
        specInfo.free();
        MemoryUtil.memFree(data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
