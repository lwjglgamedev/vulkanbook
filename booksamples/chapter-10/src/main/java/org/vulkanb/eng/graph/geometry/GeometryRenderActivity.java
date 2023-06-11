package org.vulkanb.eng.graph.geometry;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.VulkanModel;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class GeometryRenderActivity {

    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/geometry_fragment.glsl";
    private static final String GEOMETRY_FRAGMENT_SHADER_FILE_SPV = GEOMETRY_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_GLSL = "resources/shaders/geometry_vertex.glsl";
    private static final String GEOMETRY_VERTEX_SHADER_FILE_SPV = GEOMETRY_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final Device device;
    private final GeometryFrameBuffer geometryFrameBuffer;
    private final int materialSize;
    private final PipelineCache pipelineCache;
    private final Scene scene;

    private CommandBuffer[] commandBuffers;
    private DescriptorPool descriptorPool;
    private Map<String, TextureDescriptorSet> descriptorSetMap;
    private Fence[] fences;
    private DescriptorSetLayout[] geometryDescriptorSetLayouts;
    private DescriptorSetLayout.DynUniformDescriptorSetLayout materialDescriptorSetLayout;
    private VulkanBuffer materialsBuffer;
    private DescriptorSet.DynUniformDescriptorSet materialsDescriptorSet;
    private Pipeline pipeLine;
    private DescriptorSet.UniformDescriptorSet projMatrixDescriptorSet;
    private VulkanBuffer projMatrixUniform;
    private ShaderProgram shaderProgram;
    private SwapChain swapChain;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;
    private VulkanBuffer[] viewMatricesBuffer;
    private DescriptorSet.UniformDescriptorSet[] viewMatricesDescriptorSets;

    public GeometryRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache, Scene scene) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        this.scene = scene;
        device = swapChain.getDevice();
        geometryFrameBuffer = new GeometryFrameBuffer(swapChain);
        int numImages = swapChain.getNumImages();
        materialSize = calcMaterialsUniformSize();
        createShaders();
        createDescriptorPool();
        createDescriptorSets(numImages);
        createPipeline();
        createCommandBuffers(commandPool, numImages);
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
    }

    private int calcMaterialsUniformSize() {
        PhysicalDevice physDevice = device.getPhysicalDevice();
        long minUboAlignment = physDevice.getVkPhysicalDeviceProperties().limits().minUniformBufferOffsetAlignment();
        long mult = (GraphConstants.VEC4_SIZE * 9) / minUboAlignment + 1;
        return (int) (mult * minUboAlignment);
    }

    public void cleanup() {
        pipeLine.cleanup();
        materialsBuffer.cleanup();
        Arrays.stream(viewMatricesBuffer).forEach(VulkanBuffer::cleanup);
        projMatrixUniform.cleanup();
        textureSampler.cleanup();
        materialDescriptorSetLayout.cleanup();
        textureDescriptorSetLayout.cleanup();
        uniformDescriptorSetLayout.cleanup();
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
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(engineProps.getMaxMaterials(), VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages) {
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        materialDescriptorSetLayout = new DescriptorSetLayout.DynUniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        geometryDescriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
                materialDescriptorSetLayout,
        };

        EngineProperties engineProps = EngineProperties.getInstance();
        descriptorSetMap = new HashMap<>();
        textureSampler = new TextureSampler(device, 1, true);
        projMatrixUniform = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0);

        viewMatricesDescriptorSets = new DescriptorSet.UniformDescriptorSet[numImages];
        viewMatricesBuffer = new VulkanBuffer[numImages];
        materialsBuffer = new VulkanBuffer(device, (long) materialSize * engineProps.getMaxMaterials(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        materialsDescriptorSet = new DescriptorSet.DynUniformDescriptorSet(descriptorPool, materialDescriptorSetLayout,
                materialsBuffer, 0, materialSize);
        for (int i = 0; i < numImages; i++) {
            viewMatricesBuffer[i] = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            viewMatricesDescriptorSets[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    viewMatricesBuffer[i], 0);
        }
    }

    private void createPipeline() {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                geometryFrameBuffer.getRenderPass().getVkRenderPass(), shaderProgram, GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                true, true, GraphConstants.MAT4X4_SIZE,
                new VertexBufferStructure(), geometryDescriptorSetLayouts);
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
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GEOMETRY_FRAGMENT_SHADER_FILE_SPV),
                });
    }

    public List<Attachment> getAttachments() {
        return geometryFrameBuffer.geometryAttachments().getAttachments();
    }

    public void recordCommandBuffer(List<VulkanModel> vulkanModelList) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            int idx = swapChain.getCurrentFrame();

            FrameBuffer frameBuffer = geometryFrameBuffer.getFrameBuffer();
            Fence fence = fences[idx];
            CommandBuffer commandBuffer = commandBuffers[idx];

            fence.fenceWait();
            fence.reset();

            commandBuffer.reset();
            List<Attachment> attachments = geometryFrameBuffer.geometryAttachments().getAttachments();
            VkClearValue.Buffer clearValues = VkClearValue.calloc(attachments.size(), stack);
            for (Attachment attachment : attachments) {
                if (attachment.isDepthAttachment()) {
                    clearValues.apply(v -> v.depthStencil().depth(1.0f));
                } else {
                    clearValues.apply(v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));
                }
            }
            clearValues.flip();

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(geometryFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            commandBuffer.beginRecording();
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
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
                    .put(3, materialsDescriptorSet.getVkDescriptorSet());
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.getCamera().getViewMatrix());

            recordEntities(stack, cmdHandle, descriptorSets, vulkanModelList);

            vkCmdEndRenderPass(cmdHandle);
            commandBuffer.endRecording();
        }
    }

    private void recordEntities(MemoryStack stack, VkCommandBuffer cmdHandle, LongBuffer descriptorSets,
                                List<VulkanModel> vulkanModelList) {
        LongBuffer offsets = stack.mallocLong(1);
        offsets.put(0, 0L);
        LongBuffer vertexBuffer = stack.mallocLong(1);
        IntBuffer dynDescrSetOffset = stack.callocInt(1);
        int materialCount = 0;
        for (VulkanModel vulkanModel : vulkanModelList) {
            String modelId = vulkanModel.getModelId();
            List<Entity> entities = scene.getEntitiesByModelId(modelId);
            if (entities.isEmpty()) {
                materialCount += vulkanModel.getVulkanMaterialList().size();
                continue;
            }
            for (VulkanModel.VulkanMaterial material : vulkanModel.getVulkanMaterialList()) {
                int materialOffset = materialCount * materialSize;
                dynDescrSetOffset.put(0, materialOffset);
                TextureDescriptorSet textureDescriptorSet = descriptorSetMap.get(material.texture().getFileName());
                descriptorSets.put(2, textureDescriptorSet.getVkDescriptorSet());

                for (VulkanModel.VulkanMesh mesh : material.vulkanMeshList()) {
                    vertexBuffer.put(0, mesh.verticesBuffer().getBuffer());
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                    for (Entity entity : entities) {
                        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                                pipeLine.getVkPipelineLayout(), 0, descriptorSets, dynDescrSetOffset);

                        VulkanUtils.setMatrixAsPushConstant(pipeLine, cmdHandle, entity.getModelMatrix());
                        vkCmdDrawIndexed(cmdHandle, mesh.numIndices(), 1, 0, 0, 0);
                    }
                }
                materialCount++;
            }
        }
    }

    public void registerModels(List<VulkanModel> vulkanModelList) {
        device.waitIdle();
        int materialCount = 0;
        for (VulkanModel vulkanModel : vulkanModelList) {
            for (VulkanModel.VulkanMaterial vulkanMaterial : vulkanModel.getVulkanMaterialList()) {
                int materialOffset = materialCount * materialSize;
                updateTextureDescriptorSet(vulkanMaterial.texture());
                updateMaterialsBuffer(materialsBuffer, vulkanMaterial, materialOffset);
                materialCount++;
            }
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

    private void updateMaterialsBuffer(VulkanBuffer vulkanBuffer, VulkanModel.VulkanMaterial material, int offset) {
        long mappedMemory = vulkanBuffer.map();
        ByteBuffer materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        material.diffuseColor().get(offset, materialBuffer);
        vulkanBuffer.unMap();
    }

    private void updateTextureDescriptorSet(Texture texture) {
        String textureFileName = texture.getFileName();
        TextureDescriptorSet textureDescriptorSet = descriptorSetMap.get(textureFileName);
        if (textureDescriptorSet == null) {
            textureDescriptorSet = new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout,
                    texture, textureSampler, 0);
            descriptorSetMap.put(textureFileName, textureDescriptorSet);
        }
    }
}