package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class DescriptorPool {
    private static final Logger LOGGER = LogManager.getLogger();

    private Device device;
    private long vkDescriptorPool;

    public DescriptorPool(Device device, List<DescriptorTypeCount> descriptorTypeCounts) {
        LOGGER.debug("Creating descriptor pool");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int maxSets = 0;
            int numTypes = descriptorTypeCounts.size();
            VkDescriptorPoolSize.Buffer typeCounts = VkDescriptorPoolSize.callocStack(numTypes, stack);
            for (int i = 0; i < numTypes; i++) {
                maxSets += descriptorTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descriptorTypeCounts.get(i).descriptorType())
                        .descriptorCount(descriptorTypeCounts.get(i).count());
            }

            VkDescriptorPoolCreateInfo descriptorPoolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets);

            LongBuffer pDescriptorPool = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorPool(device.getVkDevice(), descriptorPoolInfo, null, pDescriptorPool),
                    "Failed to create descriptor pool");
            vkDescriptorPool = pDescriptorPool.get(0);
        }
    }

    public void cleanup() {
        LOGGER.debug("Destroying descriptor pool");
        vkDestroyDescriptorPool(device.getVkDevice(), vkDescriptorPool, null);
    }

    public Device getDevice() {
        return device;
    }

    public long getVkDescriptorPool() {
        return vkDescriptorPool;
    }

    public record DescriptorTypeCount(int count, int descriptorType) {
    }
}
