package org.vulkanb.eng.graph.anim;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.scene.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class AnimRender {

    private static final String COMPUTE_SHADER_FILE_GLSL = "resources/shaders/anim_comp.glsl";
    private static final String COMPUTE_SHADER_FILE_SPV = COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 5;

    private final CmdBuffer cmdBuffer;
    private final CmdPool cmdPool;
    private final Queue.ComputeQueue computeQueue;
    private final Fence fence;
    private final Map<String, Integer> grpSizeMap;
    private final ComputePipeline pipeline;
    private final ByteBuffer pushConstBuff;
    private final DescSetLayout stDescSetLayout;

    public AnimRender(VkCtx vkCtx) {
        fence = new Fence(vkCtx, true);
        computeQueue = new Queue.ComputeQueue(vkCtx, 0);

        cmdPool = new CmdPool(vkCtx, computeQueue.getQueueFamilyIndex(), false);
        cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);

        stDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_COMPUTE_BIT));

        ShaderModule shaderModule = createShaderModule(vkCtx);
        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);

        CompPipelineBuildInfo buildInfo = new CompPipelineBuildInfo(shaderModule, new DescSetLayout[]{
                stDescSetLayout, stDescSetLayout, stDescSetLayout, stDescSetLayout}, PUSH_CONSTANTS_SIZE);
        pipeline = new ComputePipeline(vkCtx, buildInfo);
        shaderModule.cleanup(vkCtx);
        grpSizeMap = new HashMap<>();
    }

    private static ShaderModule createShaderModule(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_compute_shader);
        }
        return new ShaderModule(vkCtx, VK_SHADER_STAGE_COMPUTE_BIT, COMPUTE_SHADER_FILE_SPV, null);
    }

    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pushConstBuff);
        pipeline.cleanup(vkCtx);
        stDescSetLayout.cleanup(vkCtx);
        fence.cleanup(vkCtx);
        cmdBuffer.cleanup(vkCtx, cmdPool);
        cmdPool.cleanup(vkCtx);
    }

    public void loadModels(ModelsCache modelsCache) {
        var models = modelsCache.getModelsMap().values();
        for (VulkanModel vulkanModel : models) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            for (VulkanMesh mesh : vulkanModel.getVulkanMeshList()) {
                int vertexSize = 14 * VkUtils.FLOAT_SIZE;
                int groupSize = (int) Math.ceil(((float) mesh.verticesBuffer().getRequestedSize() / vertexSize) /
                        LOCAL_SIZE_X);
                grpSizeMap.put(mesh.id(), groupSize);
            }
        }
    }

    private void recordingStart(VkCtx vkCtx) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    private void recordingStop() {
        cmdBuffer.endRecording();
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, ModelsCache modelsCache, AnimationsCache animationsCache) {
        fence.fenceWait(vkCtx);
        fence.reset(vkCtx);

        try (var stack = MemoryStack.stackPush()) {
            recordingStart(vkCtx);

            VkUtils.memoryBarrier(cmdBuffer, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, VK_ACCESS_SHADER_WRITE_BIT, 0);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getVkPipeline());

            Scene scene = engCtx.scene();

            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();
            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                String modelId = entity.getModelId();
                VulkanModel model = modelsCache.getModel(modelId);
                EntityAnimation entityAnimation = entity.getEntityAnimation();
                if (entityAnimation == null || !model.hasAnimations()) {
                    continue;
                }
                VulkanAnimation animation = model.getVulkanAnimationList().get(entityAnimation.getAnimationIdx());
                long jointsBuffAddress = animation.frameBufferList().get(entityAnimation.getCurrentFrame()).getAddress();
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);

                    setPushConstants(cmdHandle,
                            vulkanMesh.verticesBuffer().getAddress(),
                            vulkanMesh.weightsBuffer().getAddress(),
                            jointsBuffAddress,
                            animationsCache.getBuffer(entity.getId(), vulkanMesh.id()).getAddress(),
                            vulkanMesh.verticesBuffer().getRequestedSize() / VkUtils.FLOAT_SIZE);

                    vkCmdDispatch(cmdHandle, grpSizeMap.get(vulkanMesh.id()), 1, 1);
                }
            }
            recordingStop();

            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuffer.getVkCommandBuffer());
            computeQueue.submit(cmds, null, null, fence);
        }
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, long srcBufAddress, long weightsBufAddress,
                                  long jointsBufAddress, long dstAddress, long srcBuffFloatSize) {
        int offset = 0;
        pushConstBuff.putLong(offset, srcBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, weightsBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, jointsBufAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, dstAddress);
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, srcBuffFloatSize);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_COMPUTE_BIT, 0,
                pushConstBuff);
    }
}