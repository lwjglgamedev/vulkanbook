package org.vulkanb.eng.graph.post;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.VkUtils;

import java.nio.ByteBuffer;

public class SpecConstants {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public SpecConstants() {
        var engCfg = EngCfg.getInstance();
        data = MemoryUtil.memAlloc(VkUtils.INT_SIZE);
        data.putInt(engCfg.isFxaa() ? 1 : 0);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(1);
        specEntryMap.get(0)
                .constantID(0)
                .size(VkUtils.INT_SIZE)
                .offset(0);

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
