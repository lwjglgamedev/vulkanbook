package org.vulkanb.eng.graph.vk;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class DescriptorPool {
    private static final Logger LOGGER = LogManager.getLogger();

    private Device device;
    private long vkDescriptorPool;

    public DescriptorPool(Device device, int numStaticSets, int numDynamicSets) {
        LOGGER.debug("Creating descriptor pool");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            List<Integer> typeList = new ArrayList<>();
            List<Integer> countList = new ArrayList<>();
            if (numStaticSets > 0) {
                typeList.add(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                countList.add(numStaticSets);
            }
            if (numDynamicSets > 0) {
                typeList.add(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC);
                countList.add(numDynamicSets);
            }
            int numTypes = typeList.size();
            VkDescriptorPoolSize.Buffer typeCounts = VkDescriptorPoolSize.callocStack(numTypes, stack);
            for (int i = 0; i < numTypes; i++) {
                typeCounts.get(i)
                        .type(typeList.get(i))
                        .descriptorCount(countList.get(i));
            }

            VkDescriptorPoolCreateInfo descriptorPoolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(typeCounts)
                    .maxSets(numStaticSets + numDynamicSets);

            LongBuffer pDescriptorPool = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorPool(device.getVkDevice(), descriptorPoolInfo, null, pDescriptorPool),
                    "Failed to create descriptor pool");
            this.vkDescriptorPool = pDescriptorPool.get(0);
        }
    }

    public void cleanUp() {
        LOGGER.debug("Destroying descriptor pool");
        vkDestroyDescriptorPool(this.device.getVkDevice(), this.vkDescriptorPool, null);
    }

    public Device getDevice() {
        return this.device;
    }

    public long getVkDescriptorPool() {
        return this.vkDescriptorPool;
    }
}