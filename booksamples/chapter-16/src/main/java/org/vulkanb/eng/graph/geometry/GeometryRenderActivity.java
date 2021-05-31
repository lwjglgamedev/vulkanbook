package org.vulkanb.eng.graph.geometry;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

import java.nio.LongBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryRenderActivity {

    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/geometry_fragment.glsl";
    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_SPV = GEOMETRY_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_GLSL = "resources/shaders/geometry_vertex.glsl";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_SPV = GEOMETRY_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final Device device;
    private final GeometryFrameBuffer geometryFrameBuffer;
    private final GeometrySpecConstants geometrySpecConstants;
    private final int materialSize;
    private final MemoryBarrier memoryBarrier;
    private final PipelineCache pipelineCache;
    private final Scene scene;

    private CommandBuffer[] commandBuffers;
    private DescriptorPool descriptorPool;
    private Map<String, TextureDescriptorSet> descriptorSetMap;
    private Fence[] fences;
    private DescriptorSetLayout[] geometryDescriptorSetLayouts;
    private DescriptorSet.StorageDescriptorSet indCommandsDescriptorSet;
    private DescriptorSetLayout.DynUniformDescriptorSetLayout materialDescriptorSetLayout;
    private DescriptorSet.StorageDescriptorSet materialsDescriptorSet;
    private Pipeline pipeLine;
    private DescriptorSet.UniformDescriptorSet projMatrixDescriptorSet;
    private VulkanBuffer projMatrixUniform;
    private ShaderProgram shaderProgram;
    private DescriptorSetLayout.StorageDescriptorSetLayout storageDescriptorSetLayout;
    private SwapChain swapChain;
    private TextureDescriptorSet textureDescriptorSet;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;
    private VulkanBuffer[] viewMatricesBuffer;
    private DescriptorSet.UniformDescriptorSet[] viewMatricesDescriptorSets;

    public GeometryRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache, Scene scene,
                                  GlobalBuffers globalBuffers) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        this.scene = scene;
        device = swapChain.getDevice();
        geometrySpecConstants = new GeometrySpecConstants();

        geometryFrameBuffer = new GeometryFrameBuffer(swapChain);
        int numImages = swapChain.getNumImages();
        materialSize = calcMaterialsUniformSize();
        createShaders();
        createDescriptorPool();
        createDescriptorSets(numImages, globalBuffers);
        createPipeline();
        createCommandBuffers(commandPool, numImages);
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
        memoryBarrier = new MemoryBarrier(VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT);
    }

    public CommandBuffer beginRecording() {
        int idx = swapChain.getCurrentFrame();

        Fence fence = fences[idx];
        CommandBuffer commandBuffer = commandBuffers[idx];

        fence.fenceWait();
        fence.reset();

        commandBuffer.reset();
        commandBuffer.beginRecording();

        return commandBuffer;
    }

    private int calcMaterialsUniformSize() {
        PhysicalDevice physDevice = device.getPhysicalDevice();
        long minUboAlignment = physDevice.getVkPhysicalDeviceProperties().limits().minUniformBufferOffsetAlignment();
        long mult = (GraphConstants.VEC4_SIZE * 9) / minUboAlignment + 1;
        return (int) (mult * minUboAlignment);
    }

    public void cleanup() {
        pipeLine.cleanup();
        geometrySpecConstants.cleanup();
        Arrays.stream(viewMatricesBuffer).forEach(VulkanBuffer::cleanup);
        projMatrixUniform.cleanup();
        textureSampler.cleanup();
        materialDescriptorSetLayout.cleanup();
        textureDescriptorSetLayout.cleanup();
        uniformDescriptorSetLayout.cleanup();
        storageDescriptorSetLayout.cleanup();
        descriptorPool.cleanup();
        shaderProgram.cleanup();
        geometryFrameBuffer.cleanup();
        Arrays.stream(commandBuffers).forEach(CommandBuffer::cleanup);
        Arrays.stream(fences).forEach(Fence::cleanup);
    }

    private void createCommandBuffers(CommandPool commandPool, int numImages) {
        commandBuffers = new CommandBuffer[numImages];
        fences = new Fence[numImages];

        for (int i = 0; i < numImages; i++) {
            commandBuffers[i] = new CommandBuffer(commandPool, true, false);
            fences[i] = new Fence(device, true);
        }
    }

    private void createDescriptorPool() {
        EngineProperties engineProps = EngineProperties.getInstance();
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(swapChain.getNumImages() + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(engineProps.getMaxMaterials() * 3, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages, GlobalBuffers globalBuffers) {
        EngineProperties engineProperties = EngineProperties.getInstance();
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, engineProperties.getMaxTextures(), 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        materialDescriptorSetLayout = new DescriptorSetLayout.DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        storageDescriptorSetLayout = new DescriptorSetLayout.StorageDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        geometryDescriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                storageDescriptorSetLayout,
                textureDescriptorSetLayout,
        };

        descriptorSetMap = new HashMap<>();
        textureSampler = new TextureSampler(device, 1);
        projMatrixUniform = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0);
        indCommandsDescriptorSet = new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout,
                globalBuffers.getIndirectBuffer(), 0);
        materialsDescriptorSet = new DescriptorSet.StorageDescriptorSet(descriptorPool, storageDescriptorSetLayout,
                globalBuffers.getMaterialsBuffer(), 0);

        viewMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        viewMatricesBuffer = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            viewMatricesBuffer[i] = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            viewMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    viewMatricesBuffer[i], 0);
        }
    }

    private void createPipeline() {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                geometryFrameBuffer.getRenderPass().getVkRenderPass(), shaderProgram, GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                true, true, 0,
                new InstancedVertexBufferStructure(), geometryDescriptorSetLayouts);
        pipeLine = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanup();
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(GEOMETRY_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GEOMETRY_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV,
                                geometrySpecConstants.getSpecInfo()),
                });
    }

    public void endRecording(CommandBuffer commandBuffer) {
        commandBuffer.endRecording();
    }

    public List<Attachment> getAttachments() {
        return geometryFrameBuffer.geometryAttachments().getAttachments();
    }

    public void loadModels(TextureCache textureCache) {
        device.waitIdle();
        // Size of the descriptor is setup in the layout, we need to fill up the texture list
        // up to the number defined in the layout (reusing last texture)
        List<Texture> textureList = new ArrayList<>();
        List<Texture> textureCacheList = textureCache.getAsList();
        int textureCacheSize = textureCacheList.size();
        textureList.addAll(textureCacheList);
        EngineProperties engineProperties = EngineProperties.getInstance();
        int maxTextures = engineProperties.getMaxTextures();
        for (int i = 0; i < maxTextures - textureCacheSize; i++) {
            textureList.add(textureCacheList.get(textureCacheSize - 1));
        }
        textureDescriptorSet = new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, textureList,
                textureSampler, 0);
    }

    // TODO: Check if commands can be pre-recorded
    public void recordCommandBuffer(CommandBuffer commandBuffer, GlobalBuffers globalBuffers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            int idx = swapChain.getCurrentFrame();

            FrameBuffer frameBuffer = geometryFrameBuffer.getFrameBuffer();
            List<Attachment> attachments = geometryFrameBuffer.geometryAttachments().getAttachments();
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(attachments.size(), stack);
            for (Attachment attachment : attachments) {
                if (attachment.isDepthAttachment()) {
                    clearValues.apply(v -> v.depthStencil().depth(1.0f));
                } else {
                    clearValues.apply(v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));
                }
            }
            clearValues.flip();

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(geometryFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdPipelineBarrier(cmdHandle, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_VERTEX_INPUT_BIT,
                    0, memoryBarrier.getVkMemoryBarrier(), null, null);

            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.callocStack(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height))
                    .offset(it -> it
                            .x(0)
                            .y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, projMatrixDescriptorSet.getVkDescriptorSet())
                    .put(1, viewMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, materialsDescriptorSet.getVkDescriptorSet())
                    .put(3, textureDescriptorSet.getVkDescriptorSet());

            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.getCamera().getViewMatrix());

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

            LongBuffer vertexBuffer = stack.mallocLong(1);
            LongBuffer instanceBuffer = stack.mallocLong(1);
            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);

            // Draw commands for non animated models
            vertexBuffer.put(0, globalBuffers.getVerticesBuffer().getBuffer());
            instanceBuffer.put(0, globalBuffers.getInstanceDataBuffer().getBuffer());

            vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
            vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
            VulkanBuffer staticIndirectBuffer = globalBuffers.getIndirectBuffer();
            vkCmdDrawIndexedIndirect(cmdHandle, staticIndirectBuffer.getBuffer(), 0, globalBuffers.getNumIndirectCommands(),
                    GlobalBuffers.IND_COMMAND_STRIDE);

            // Draw commands for  animated models
            vertexBuffer.put(0, globalBuffers.getAnimVerticesBuffer().getBuffer());
            instanceBuffer.put(0, globalBuffers.getAnimInstanceDataBuffer().getBuffer());

            vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
            vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
            VulkanBuffer animIndirectBuffer = globalBuffers.getAnimIndirectBuffer();
            vkCmdDrawIndexedIndirect(cmdHandle, animIndirectBuffer.getBuffer(), 0, globalBuffers.getNumAnimIndirectCommands(),
                    GlobalBuffers.IND_COMMAND_STRIDE);

            vkCmdEndRenderPass(cmdHandle);
        }
    }

    public void resize(SwapChain swapChain) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
        this.swapChain = swapChain;
        geometryFrameBuffer.resize(swapChain);
    }

    public void submit(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = swapChain.getCurrentFrame();
            CommandBuffer commandBuffer = commandBuffers[idx];
            Fence currentFence = fences[idx];
            SwapChain.SyncSemaphores syncSemaphores = swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()), currentFence);
        }
    }

}