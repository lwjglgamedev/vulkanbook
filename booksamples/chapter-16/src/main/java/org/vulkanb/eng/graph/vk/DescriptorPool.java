package org.vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class DescriptorPool {
    private final Device device;
    private final long vkDescriptorPool;

    public DescriptorPool(Device device, List<DescriptorTypeCount> descriptorTypeCounts) {
        Logger.debug("Creating descriptor pool");
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int maxSets = 0;
            int numTypes = descriptorTypeCounts.size();
            VkDescriptorPoolSize.Buffer typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack);
            for (int i = 0; i < numTypes; i++) {
                maxSets += descriptorTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descriptorTypeCounts.get(i).descriptorType())
                        .descriptorCount(descriptorTypeCounts.get(i).count());
            }

            VkDescriptorPoolCreateInfo descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets);

            LongBuffer pDescriptorPool = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorPool(device.getVkDevice(), descriptorPoolInfo, null, pDescriptorPool),
                    "Failed to create descriptor pool");
            vkDescriptorPool = pDescriptorPool.get(0);
        }
    }

    public void cleanup() {
        Logger.debug("Destroying descriptor pool");
        vkDestroyDescriptorPool(device.getVkDevice(), vkDescriptorPool, null);
    }

    public void freeDescriptorSet(long vkDescriptorSet) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longBuffer = stack.mallocLong(1);
            longBuffer.put(0, vkDescriptorSet);

            vkCheck(vkFreeDescriptorSets(device.getVkDevice(), vkDescriptorPool, longBuffer),
                    "Failed to free descriptor set");
        }
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
