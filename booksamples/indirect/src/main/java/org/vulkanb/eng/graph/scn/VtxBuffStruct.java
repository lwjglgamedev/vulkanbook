package org.vulkanb.eng.graph.scn;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.VkUtils;

import static org.lwjgl.vulkan.VK13.*;

public class VtxBuffStruct {

    public static final int TEXT_COORD_COMPONENTS = 2;
    private static final int NORMAL_COMPONENTS = 3;
    private static final int NUMBER_OF_ATTRIBUTES = 10;
    private static final int POSITION_COMPONENTS = 3;
    public static final int SIZE_IN_BYTES = (POSITION_COMPONENTS + NORMAL_COMPONENTS * 3 + TEXT_COORD_COMPONENTS) * VkUtils.FLOAT_SIZE;

    private final VkPipelineVertexInputStateCreateInfo vi;
    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public VtxBuffStruct() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(2);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

        // Normal
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(POSITION_COMPONENTS * VkUtils.FLOAT_SIZE);

        // Tangent
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(NORMAL_COMPONENTS * VkUtils.FLOAT_SIZE + POSITION_COMPONENTS * VkUtils.FLOAT_SIZE);

        // BiTangent
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(NORMAL_COMPONENTS * VkUtils.FLOAT_SIZE * 2 + POSITION_COMPONENTS * VkUtils.FLOAT_SIZE);

        // Texture coordinates
        i++;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(NORMAL_COMPONENTS * VkUtils.FLOAT_SIZE * 3 + POSITION_COMPONENTS * VkUtils.FLOAT_SIZE);

        // Model Matrix as a set of 4 Vectors
        i++;
        for (int j = 0; j < 4; j++) {
            viAttrs.get(i)
                    .binding(1)
                    .location(i)
                    .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                    .offset(j * VkUtils.VEC4_SIZE);
            i++;
        }
        viAttrs.get(i)
                .binding(1)
                .location(i)
                .format(VK_FORMAT_R8_UINT)
                .offset(VkUtils.VEC4_SIZE * 4);

        // Non instanced data
        viBindings.get(0)
                .binding(0)
                .stride(SIZE_IN_BYTES)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        // Instanced data
        viBindings.get(1)
                .binding(1)
                .stride(VkUtils.MAT4X4_SIZE + VkUtils.INT_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_INSTANCE);

        vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    public void cleanup() {
        viBindings.free();
        viAttrs.free();
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}