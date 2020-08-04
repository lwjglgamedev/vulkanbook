package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class MaterialDescriptorSetLayout extends DescriptorSetLayout {

    private static final Logger LOGGER = LogManager.getLogger();

    private int materialSize;

    public MaterialDescriptorSetLayout(Device device, int binding) {
        super(device);

        materialSize = calcMaterialsUniformSize(device.getPhysicalDevice());

        LOGGER.debug("Creating material descriptor set layout");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.callocStack(1, stack);
            layoutBindings.get(0)
                    .binding(binding)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, lp),
                    "Failed to create material descriptor set layout");
            super.vkDescriptorLayout = lp.get(0);
        }
    }

    private static int calcMaterialsUniformSize(PhysicalDevice physDevice) {
        long minUboAlignment = physDevice.getVkPhysicalDeviceProperties().limits().minUniformBufferOffsetAlignment();
        long mult = GraphConstants.VEC4_SIZE / minUboAlignment + 1;
        return (int) (mult * minUboAlignment);
    }

    public int getMaterialSize() {
        return materialSize;
    }
}
