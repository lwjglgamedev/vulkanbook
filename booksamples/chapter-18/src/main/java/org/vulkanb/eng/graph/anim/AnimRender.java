package org.vulkanb.eng.graph.anim;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.scn.VtxBuffStruct;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.List;

import static org.lwjgl.vulkan.VK13.*;

public class AnimRender {

    private static final String COMPUTE_SHADER_FILE_GLSL = "resources/shaders/anim_comp.glsl";
    private static final String COMPUTE_SHADER_FILE_SPV = COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final String DESC_ID_DST_VTX = "ANIM_DESC_ID_DST_VTX";
    private static final String DESC_ID_JOINTS = "ANIM_DESC_ID_JOINTS";
    private static final String DESC_ID_SRC_VTX = "ANIM_DESC_ID_SRC_VTX";
    private static final String DESC_ID_WEIGHTS = "ANIM_DESC_ID_WEIGHTS";
    private static final int LOCAL_SIZE_X = 32;
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.INT_SIZE * 5;

    private final CmdBuffer cmdBuffer;
    private final CmdPool cmdPool;
    private final Queue.ComputeQueue computeQueue;
    private final Fence fence;
    private final ComputePipeline pipeline;
    private final DescSetLayout stDescSetLayout;

    public AnimRender(VkCtx vkCtx) {
        fence = new Fence(vkCtx, true);
        computeQueue = new Queue.ComputeQueue(vkCtx, 0);

        cmdPool = new CmdPool(vkCtx, computeQueue.getQueueFamilyIndex(), false);
        cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);

        stDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_COMPUTE_BIT));

        ShaderModule shaderModule = createShaderModule(vkCtx);
        CompPipelineBuildInfo buildInfo = new CompPipelineBuildInfo(shaderModule, new DescSetLayout[]{
                stDescSetLayout, stDescSetLayout, stDescSetLayout, stDescSetLayout}, PUSH_CONSTANTS_SIZE);
        pipeline = new ComputePipeline(vkCtx, buildInfo);
        shaderModule.cleanup(vkCtx);
    }

    private static ShaderModule createShaderModule(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_compute_shader);
        }
        return new ShaderModule(vkCtx, VK_SHADER_STAGE_COMPUTE_BIT, COMPUTE_SHADER_FILE_SPV, null);
    }

    public void cleanup(VkCtx vkCtx) {
        pipeline.cleanup(vkCtx);
        stDescSetLayout.cleanup(vkCtx);
        fence.cleanup(vkCtx);
        cmdBuffer.cleanup(vkCtx, cmdPool);
        cmdPool.cleanup(vkCtx);
    }

    public void loadEntities(VkCtx vkCtx, GlobalBuffers globalBuffers) {
        if (globalBuffers.getAnimVerticesBuffer() == null) {
            return;
        }
        Device device = vkCtx.getDevice();
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        DescSetLayout.LayoutInfo layoutInfo = stDescSetLayout.getLayoutInfo();
        DescSet descSet = descAllocator.addDescSet(device, DESC_ID_SRC_VTX, stDescSetLayout);
        descSet.setBuffer(device, globalBuffers.getVerticesBuffer(), globalBuffers.getVerticesBuffer().getRequestedSize(),
                layoutInfo.binding(), layoutInfo.descType());
        descSet = descAllocator.addDescSet(device, DESC_ID_WEIGHTS, stDescSetLayout);
        descSet.setBuffer(device, globalBuffers.getAnimWeightsBuffer(), globalBuffers.getAnimWeightsBuffer().getRequestedSize(),
                layoutInfo.binding(), layoutInfo.descType());
        descSet = descAllocator.addDescSet(device, DESC_ID_DST_VTX, stDescSetLayout);
        descSet.setBuffer(device, globalBuffers.getAnimVerticesBuffer(), globalBuffers.getAnimVerticesBuffer().getRequestedSize(),
                layoutInfo.binding(), layoutInfo.descType());
        descSet = descAllocator.addDescSet(device, DESC_ID_JOINTS, stDescSetLayout);
        descSet.setBuffer(device, globalBuffers.getAnimJointMatricesBuffer(), globalBuffers.getAnimJointMatricesBuffer().getRequestedSize(),
                layoutInfo.binding(), layoutInfo.descType());
    }

    private void recordingStart(VkCtx vkCtx) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    private void recordingStop() {
        cmdBuffer.endRecording();
    }

    public void render(VkCtx vkCtx, GlobalBuffers globalBuffers) {
        if (globalBuffers.getAnimVerticesBuffer() == null) {
            return;
        }

        fence.fenceWait(vkCtx);
        fence.reset(vkCtx);

        try (var stack = MemoryStack.stackPush()) {
            recordingStart(vkCtx);

            VkUtils.setMemBarrier(cmdBuffer, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, VK_ACCESS_SHADER_WRITE_BIT, 0);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getVkPipeline());

            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(4);
            descriptorSets.put(descAllocator.getDescSet(DESC_ID_SRC_VTX).getVkDescriptorSet());
            descriptorSets.put(descAllocator.getDescSet(DESC_ID_WEIGHTS).getVkDescriptorSet());
            descriptorSets.put(descAllocator.getDescSet(DESC_ID_DST_VTX).getVkDescriptorSet());
            descriptorSets.put(descAllocator.getDescSet(DESC_ID_JOINTS).getVkDescriptorSet());
            descriptorSets.flip();
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            List<VulkanAnimEntity> vulkanAnimEntityList = globalBuffers.getVulkanAnimEntityList();
            for (VulkanAnimEntity vulkanAnimEntity : vulkanAnimEntityList) {
                Entity entity = vulkanAnimEntity.getEntity();
                EntityAnimation entityAnimation = entity.getEntityAnimation();
                if (!entityAnimation.isStarted()) {
                    continue;
                }

                VulkanModel vulkanModel = vulkanAnimEntity.getVulkanModel();
                int animationIdx = entity.getEntityAnimation().getAnimationIdx();
                int currentFrame = entity.getEntityAnimation().getCurrentFrame();
                int jointMatricesOffset = vulkanModel.getVulkanAnimationList().get(animationIdx).getVulkanAnimationFrameList().get(currentFrame).jointMatricesOffset();

                for (VulkanAnimEntity.VulkanAnimMesh vulkanAnimMesh : vulkanAnimEntity.getVulkanAnimMeshList()) {
                    VulkanMesh mesh = vulkanAnimMesh.vulkanMesh();

                    int groupSize = (int) Math.ceil((mesh.verticesSize() / (float) VtxBuffStruct.SIZE_IN_BYTES) / LOCAL_SIZE_X);

                    // Push constants
                    ByteBuffer pushConstantBuffer = stack.malloc(PUSH_CONSTANTS_SIZE);
                    pushConstantBuffer.putInt(mesh.verticesOffset() / VkUtils.FLOAT_SIZE);
                    pushConstantBuffer.putInt(mesh.verticesSize() / VkUtils.FLOAT_SIZE);
                    pushConstantBuffer.putInt(mesh.weightsOffset() / VkUtils.FLOAT_SIZE);
                    pushConstantBuffer.putInt(jointMatricesOffset / VkUtils.MAT4X4_SIZE);
                    pushConstantBuffer.putInt(vulkanAnimMesh.meshOffset() / VkUtils.FLOAT_SIZE);
                    pushConstantBuffer.flip();
                    vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(),
                            VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstantBuffer);

                    vkCmdDispatch(cmdHandle, groupSize, 1, 1);
                }
            }

            recordingStop();

            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuffer.getVkCommandBuffer());
            computeQueue.submit(cmds, null, null, fence);
        }
    }
}
