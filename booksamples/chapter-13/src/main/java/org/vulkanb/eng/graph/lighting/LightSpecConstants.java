package org.vulkanb.eng.graph.lighting;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class LightSpecConstants {

    private ByteBuffer data;

    private VkSpecializationMapEntry.Buffer specEntryMap;
    private VkSpecializationInfo specInfo;

    public LightSpecConstants() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH * 3 + GraphConstants.FLOAT_LENGTH);
        data.putInt(GraphConstants.MAX_LIGHTS);
        data.putInt(GraphConstants.SHADOW_MAP_CASCADE_COUNT);
        data.putInt(engineProperties.isShadowPcf() ? 1 : 0);
        data.putFloat(engineProperties.getShadowBias());
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(4);
        specEntryMap.get(0)
                .constantID(0)
                .size(GraphConstants.INT_LENGTH)
                .offset(0);
        specEntryMap.get(1)
                .constantID(1)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH);
        specEntryMap.get(2)
                .constantID(2)
                .size(GraphConstants.INT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 2);
        specEntryMap.get(3)
                .constantID(3)
                .size(GraphConstants.FLOAT_LENGTH)
                .offset(GraphConstants.INT_LENGTH * 3);

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
