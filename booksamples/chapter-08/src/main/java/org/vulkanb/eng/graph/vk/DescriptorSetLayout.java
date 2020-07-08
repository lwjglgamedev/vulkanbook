package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;

import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;

public abstract class DescriptorSetLayout {

    private static final Logger LOGGER = LogManager.getLogger();
    protected long vkDescriptorLayout;
    private Device device;

    public DescriptorSetLayout(Device device) {
        this.device = device;
    }

    public void cleanUp() {
        LOGGER.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(this.device.getVkDevice(), this.vkDescriptorLayout, null);
    }

    public long getVkDescriptorLayout() {
        return this.vkDescriptorLayout;
    }
}
