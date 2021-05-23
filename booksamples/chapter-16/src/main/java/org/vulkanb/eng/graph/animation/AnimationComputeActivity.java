package org.vulkanb.eng.graph.animation;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class AnimationComputeActivity {

    private static final String ANIM_COMPUTE_SHADER_FILE_GLSL = "resources/shaders/animations_comp.glsl";
    private static final String ANIM_COMPUTE_SHADER_FILE_SPV = ANIM_COMPUTE_SHADER_FILE_GLSL + ".spv";
    private static final int LOCAL_SIZE_X = 32;
    private final Queue.ComputeQueue computeQueue;
    private final Device device;
    // Key is the entity id
    private final Map<String, List<EntityAnimationBuffer>> entityAnimationsBuffers;
    private final MemoryBarrier memoryBarrier;
    // Key is the model id
    private final Map<String, ModelDescriptorSets> modelDescriptorSetsMap;
    private final Scene scene;

    private CommandBuffer commandBuffer;
    private ComputePipeline computePipeline;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Fence fence;
    private ShaderProgram shaderProgram;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public AnimationComputeActivity(CommandPool commandPool, PipelineCache pipelineCache, Scene scene) {
        this.scene = scene;
        device = pipelineCache.getDevice();
        computeQueue = new Queue.ComputeQueue(device, 0);
        createDescriptorPool();
        createDescriptorSets();
        createShaders();
        createPipeline(pipelineCache);
        createCommandBuffers(commandPool);
        modelDescriptorSetsMap = new HashMap<>();
        entityAnimationsBuffers = new HashMap<>();
        memoryBarrier = new MemoryBarrier(0, VK_ACCESS_SHADER_WRITE_BIT);
    }

    public void cleanup() {
        computePipeline.cleanup();
        shaderProgram.cleanup();
        commandBuffer.cleanup();
        descriptorPool.cleanup();
        storageDescriptorSetLayout.cleanup();
        uniformDescriptorSetLayout.cleanup();
        fence.cleanup();
        for (Map.Entry<String, List<EntityAnimationBuffer>> entry : entityAnimationsBuffers.entrySet()) {
            entry.getValue().forEach(EntityAnimationBuffer::cleanup);
        }
    }

    private void createCommandBuffers(CommandPool commandPool) {
        commandBuffer = new CommandBuffer(commandPool, true, false);
        fence = new Fence(device, true);
    }

    private void createDescriptorPool() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        int maxStorageBuffers = engineProperties.getMaxStorageBuffers();
        int maxJointsMatricesLists = engineProperties.getMaxJointsMatricesLists();
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        // TODO: Max storage buffers should be fixed now
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(maxStorageBuffers, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(maxJointsMatricesLists, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets() {
        storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_COMPUTE_BIT);
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_COMPUTE_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                storageDescriptorSetLayout,
                storageDescriptorSetLayout,
                storageDescriptorSetLayout,
                storageDescriptorSetLayout,
                uniformDescriptorSetLayout,
        };
    }

    private void createPipeline(PipelineCache pipelineCache) {
        ComputePipeline.PipeLineCreationInfo pipeLineCreationInfo = new ComputePipeline.PipeLineCreationInfo(shaderProgram,
                descriptorSetLayouts);
        computePipeline = new ComputePipeline(pipelineCache, pipeLineCreationInfo);
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(ANIM_COMPUTE_SHADER_FILE_GLSL, Shaderc.shaderc_compute_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_COMPUTE_BIT, ANIM_COMPUTE_SHADER_FILE_SPV),
                });
    }

    public Map<String, List<EntityAnimationBuffer>> getEntityAnimationsBuffers() {
        return entityAnimationsBuffers;
    }

    /*
    public void recordCommandBuffer(List<VulkanModel> vulkanModelList) {
        fence.fenceWait();
        fence.reset();

        commandBuffer.reset();
        commandBuffer.beginRecording();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                    0, memoryBarrier.getVkMemoryBarrier(), null, null);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline.getVkPipeline());

            LongBuffer descriptorSets = stack.mallocLong(4);

            for (VulkanModel vulkanModel : vulkanModelList) {
                String modelId = vulkanModel.getModelId();
                List<Entity> entities = scene.getEntitiesByModelId(modelId);
                if (entities == null || entities.isEmpty() || !vulkanModel.hasAnimations()) {
                    continue;
                }

                ModelDescriptorSets modelDescriptorSets = modelDescriptorSetsMap.get(modelId);
                int meshCount = 0;
                for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                    for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                        MeshDescriptorSets meshDescriptorSets = modelDescriptorSets.meshesDescriptorSets.get(meshCount);
                        descriptorSets.put(0, meshDescriptorSets.srcDescriptorSet.getVkDescriptorSet());
                        descriptorSets.put(1, meshDescriptorSets.weightsDescriptorSet.getVkDescriptorSet());

                        for (Entity entity : entities) {
                            List<EntityAnimationBuffer> animationsBuffer = entityAnimationsBuffers.get(entity.getId());
                            EntityAnimationBuffer entityAnimationBuffer = animationsBuffer.get(meshCount);
                            descriptorSets.put(2, entityAnimationBuffer.descriptorSet().getVkDescriptorSet());

                            Entity.EntityAnimation entityAnimation = entity.getEntityAnimation();
                            if (!entityAnimation.isStarted()) {
                                continue;
                            }
                            DescriptorSet jointMatricesDescriptorSet = modelDescriptorSets.jointMatricesBufferDescriptorSets.
                                    get(entityAnimation.getAnimationIdx()).get(entityAnimation.getCurrentFrame());
                            descriptorSets.put(3, jointMatricesDescriptorSet.getVkDescriptorSet());

                            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE,
                                    computePipeline.getVkPipelineLayout(), 0, descriptorSets, null);

                            vkCmdDispatch(cmdHandle, meshDescriptorSets.groupSize(), 1, 1);
                        }
                        meshCount++;
                    }
                }
            }
        }
        commandBuffer.endRecording();
    }


    public void registerEntity(VulkanModel vulkanModel, Entity entity) {
        List<EntityAnimationBuffer> bufferList = new ArrayList<>();
        entityAnimationsBuffers.put(entity.getId(), bufferList);
        for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
            for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                VulkanBuffer animationBuffer = new VulkanBuffer(device, mesh.verticesBuffer().getRequestedSize(),
                        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, 0);
                DescriptorSet descriptorSet = new DescriptorSet.StorageDescriptorSet(descriptorPool,
                        storageDescriptorSetLayout, animationBuffer, 0);
                bufferList.add(new EntityAnimationBuffer(animationBuffer, descriptorSet));
            }
        }
    }
     */

    /*
    public void registerModels(List<VulkanModel> vulkanModels) {
        for (VulkanModel vulkanModel : vulkanModels) {
            if (!vulkanModel.hasAnimations()) {
                continue;
            }
            String modelId = vulkanModel.getModelId();
            List<List<DescriptorSet>> jointMatricesBufferDescriptorSets = new ArrayList<>();
            for (VulkanModel.VulkanAnimation animation : vulkanModel.getAnimationList()) {
                List<DescriptorSet> animationFrames = new ArrayList<>();
                for (VulkanBuffer jointsMatricesBuffer : animation.frameBufferList()) {
                    animationFrames.add(new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                            jointsMatricesBuffer, 0));
                }
                jointMatricesBufferDescriptorSets.add(animationFrames);
            }

            List<MeshDescriptorSets> meshDescriptorSetsList = new ArrayList<>();
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    int vertexSize = 14 * GraphConstants.FLOAT_LENGTH;
                    int groupSize = (int) Math.ceil((mesh.verticesBuffer().getRequestedSize() / vertexSize) / (float) LOCAL_SIZE_X);
                    MeshDescriptorSets meshDescriptorSets = new MeshDescriptorSets(
                            new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout, mesh.verticesBuffer(), 0),
                            groupSize,
                            new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout, mesh.weightsBuffer(), 0)
                    );
                    meshDescriptorSetsList.add(meshDescriptorSets);
                }
            }

            ModelDescriptorSets modelDescriptorSets = new ModelDescriptorSets(meshDescriptorSetsList, jointMatricesBufferDescriptorSets);
            modelDescriptorSetsMap.put(modelId, modelDescriptorSets);
        }
    }
     */

    public void submit() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            computeQueue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    null,
                    null,
                    null,
                    fence);
        }
    }

    public record EntityAnimationBuffer(VulkanBuffer verticesBuffer, DescriptorSet descriptorSet) {
        public void cleanup() {
            verticesBuffer.cleanup();
        }
    }

    record MeshDescriptorSets(DescriptorSet srcDescriptorSet, int groupSize,
                              DescriptorSet weightsDescriptorSet) {
    }

    record ModelDescriptorSets(List<MeshDescriptorSets> meshesDescriptorSets,
                               List<List<DescriptorSet>> jointMatricesBufferDescriptorSets) {
    }
}
