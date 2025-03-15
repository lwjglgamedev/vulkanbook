package org.vulkanb.eng.graph.anim;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class AnimRender {

    private static final String COMPUTE_SHADER_FILE_GLSL = "resources/shaders/anim_comp.glsl";
    private static final String COMPUTE_SHADER_FILE_SPV = COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;

    private final CmdBuffer cmdBuffer;
    private final CmdPool cmdPool;
    private final Queue.ComputeQueue computeQueue;
    private final Fence fence;
    private final Map<String, Integer> grpSizeMap;
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
                stDescSetLayout, stDescSetLayout, stDescSetLayout, stDescSetLayout}, 0);
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
        pipeline.cleanup(vkCtx);
        stDescSetLayout.cleanup(vkCtx);
        fence.cleanup(vkCtx);
        cmdBuffer.cleanup(vkCtx, cmdPool);
        cmdPool.cleanup(vkCtx);
    }

    public void loadModels(VkCtx vkCtx, ModelsCache modelsCache, List<Entity> entities, AnimationsCache animationsCache) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSetLayout.LayoutInfo layoutInfo = stDescSetLayout.getLayoutInfo();
        var models = modelsCache.getModelsMap().values();
        for (VulkanModel vulkanModel : models) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            String modelId = vulkanModel.getId();
            int animationIdx = 0;
            for (VulkanAnimation animation : vulkanModel.getVulkanAnimationList()) {
                int buffPos = 0;
                for (VkBuffer jointsMatricesBuffer : animation.frameBufferList()) {
                    String id = modelId + "_" + animationIdx + "_" + buffPos;
                    DescSet descSet = descAllocator.addDescSet(device, id, stDescSetLayout);
                    descSet.setBuffer(device, jointsMatricesBuffer, jointsMatricesBuffer.getRequestedSize(), 0,
                            layoutInfo.descType());
                    buffPos++;
                }
                animationIdx++;
            }

            for (VulkanMesh mesh : vulkanModel.getVulkanMeshList()) {
                int vertexSize = 14 * VkUtils.FLOAT_SIZE;
                int groupSize = (int) Math.ceil(((float) mesh.verticesBuffer().getRequestedSize() / vertexSize) /
                        LOCAL_SIZE_X);
                DescSet vtxDescSet = descAllocator.addDescSet(device, mesh.id() + "_VTX", stDescSetLayout);
                vtxDescSet.setBuffer(device, mesh.verticesBuffer(), mesh.verticesBuffer().getRequestedSize(), 0,
                        layoutInfo.descType());
                grpSizeMap.put(mesh.id(), groupSize);

                DescSet weightsDescSet = descAllocator.addDescSet(device, mesh.id() + "_W", stDescSetLayout);
                weightsDescSet.setBuffer(device, mesh.weightsBuffer(), mesh.weightsBuffer().getRequestedSize(), 0,
                        layoutInfo.descType());
            }
        }

        int numEntities = entities.size();
        for (int i = 0; i < numEntities; i++) {
            var entity = entities.get(i);
            VulkanModel model = modelsCache.getModel(entity.getModelId());
            if (!model.hasAnimations()) {
                continue;
            }
            List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
            int numMeshes = vulkanMeshList.size();
            for (int j = 0; j < numMeshes; j++) {
                var vulkanMesh = vulkanMeshList.get(j);
                VkBuffer animationBuffer = animationsCache.getBuffer(entity.getId(), vulkanMesh.id());
                DescSet descSet = descAllocator.addDescSet(device, entity.getId() + "_" + vulkanMesh.id() + "_ENT", stDescSetLayout);
                descSet.setBuffer(device, animationBuffer, animationBuffer.getRequestedSize(), 0, layoutInfo.descType());
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

    public void render(EngCtx engCtx, VkCtx vkCtx, ModelsCache modelsCache) {
        fence.fenceWait(vkCtx);
        fence.reset(vkCtx);

        try (var stack = MemoryStack.stackPush()) {
            recordingStart(vkCtx);

            VkUtils.memoryBarrier(cmdBuffer, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, VK_ACCESS_SHADER_WRITE_BIT, 0);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(4);

            Scene scene = engCtx.scene();
            DescAllocator descAllocator = vkCtx.getDescAllocator();

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
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    descriptorSets.put(0, descAllocator.getDescSet(vulkanMesh.id() + "_VTX").getVkDescriptorSet());
                    descriptorSets.put(1, descAllocator.getDescSet(vulkanMesh.id() + "_W").getVkDescriptorSet());
                    descriptorSets.put(2, descAllocator.getDescSet(entity.getId() + "_" + vulkanMesh.id() + "_ENT").getVkDescriptorSet());

                    String id = modelId + "_" + entityAnimation.getAnimationIdx() + "_" + entityAnimation.getCurrentFrame();
                    descriptorSets.put(3, descAllocator.getDescSet(id).getVkDescriptorSet());

                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                            pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

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
}
