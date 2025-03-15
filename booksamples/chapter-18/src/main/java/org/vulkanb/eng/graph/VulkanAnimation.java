package org.vulkanb.eng.graph;

import java.util.*;

public class VulkanAnimation {
    private final List<VulkanAnimationFrame> vulkanAnimationFrameList;

    public VulkanAnimation() {
        vulkanAnimationFrameList = new ArrayList<>();
    }

    public void addVulkanAnimationFrame(VulkanAnimationFrame vulkanAnimationFrame) {
        vulkanAnimationFrameList.add(vulkanAnimationFrame);
    }

    public List<VulkanAnimationFrame> getVulkanAnimationFrameList() {
        return vulkanAnimationFrameList;
    }
}