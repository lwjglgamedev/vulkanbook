package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class SpecializationConstants {

    private ByteBuffer data;

    private VkSpecializationMapEntry.Buffer specEntryMap;
    private VkSpecializationInfo specInfo;

    public SpecializationConstants(int numSamples) {
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 2);
        data.putInt(0, GraphConstants.MAX_LIGHTS);
        data.putInt(GraphConstants.INT_LENGTH, numSamples);

        specEntryMap = VkSpecializationMapEntry.calloc(2);
        specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
                .offset(0);
        specEntryMap.get(1)
                .constantID(1)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH);

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
