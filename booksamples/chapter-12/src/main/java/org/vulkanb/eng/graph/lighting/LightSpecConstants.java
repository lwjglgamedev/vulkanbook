package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class LightSpecConstants {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public LightSpecConstants() {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH);
        data.putInt(GraphConstants.MAX_LIGHTS);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(1);
        specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
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
