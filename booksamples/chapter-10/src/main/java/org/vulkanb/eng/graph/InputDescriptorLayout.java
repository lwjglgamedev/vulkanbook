package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK10.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class InputDescriptorLayout extends DescriptorSetLayout {

    private static final Logger LOGGER = LogManager.getLogger();

    public InputDescriptorLayout(Device device, int startBinding, int numBindings) {
        super(device);

        LOGGER.debug("Creating input attachment descriptor set layout");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.callocStack(numBindings, stack);
            for (int i = 0; i < numBindings; i++) {
                layoutBindings.get(i).binding(i + startBinding)
                        .descriptorType(VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, lp),
                    "Failed to create descriptor set layout");
            super.vkDescriptorLayout = lp.get(0);
        }
    }
}
