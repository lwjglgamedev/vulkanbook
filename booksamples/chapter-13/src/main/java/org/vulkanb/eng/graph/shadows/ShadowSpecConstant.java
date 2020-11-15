package org.vulkanb.eng.graph.shadows;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class ShadowSpecConstant {

    private ByteBuffer data;

    private VkSpecializationMapEntry.Buffer specEntryMap;
    private VkSpecializationInfo specInfo;

    public ShadowSpecConstant() {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH);
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
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
        MemoryUtil.memFree(data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
