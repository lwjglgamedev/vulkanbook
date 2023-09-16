package org.vulkanb.eng.graph.animation;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.nio.ByteBuffer;

public class AnimationSpecConstants {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public AnimationSpecConstants() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        data = MemoryUtil.memAlloc(GraphConstants.INT_LENGTH);
        data.putInt(engineProperties.getMaxJointsMatricesLists());
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
