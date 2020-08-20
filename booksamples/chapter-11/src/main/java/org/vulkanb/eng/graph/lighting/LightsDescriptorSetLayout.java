package org.vulkanb.eng.graph.lighting;

import org.apache.logging.log4j.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK11.*;
import static org.vulkanb.eng.graph.vk.VulkanUtils.vkCheck;

public class LightsDescriptorSetLayout extends DescriptorSetLayout {
    private static final Logger LOGGER = LogManager.getLogger();

    // 2 uniforms: lights, ambient light
    private static final int NUM_UNIFORMS = 2;

    public LightsDescriptorSetLayout(Device device) {
        super(device);

        LOGGER.debug("Creating Lights descriptor set Layout");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer layoutBindings = VkDescriptorSetLayoutBinding.callocStack(NUM_UNIFORMS, stack);
            for (int i = 0; i < NUM_UNIFORMS; i++) {
                layoutBindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBindings);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(device.getVkDevice(), layoutInfo, null, lp),
                    "Failed to create descriptor set layout");
            vkDescriptorLayout = lp.get(0);
        }
    }
}
