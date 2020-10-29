package org.vulkanb.eng.graph.geometry;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.*;
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

    private CommandBuffer[] commandBuffers;
    private DescriptorPool descriptorPool;
    private Map<String, TextureDescriptorSet> descriptorSetMap;
    private Device device;
    private Fence[] fences;
    private DescriptorSetLayout[] geometryDescriptorSetLayouts;
    private GeometryFrameBuffer geometryFrameBuffer;
    private MaterialDescriptorSetLayout materialDescriptorSetLayout;
    private VulkanBuffer materialsBuffer;
    private MaterialDescriptorSet materialsDescriptorSet;
    private MatrixDescriptorSetLayout matrixDescriptorSetLayout;
    private Pipeline pipeLine;
    private PipelineCache pipelineCache;
    private MatrixDescriptorSet projMatrixDescriptorSet;
    private VulkanBuffer projMatrixUniform;
    private ShaderProgram shaderProgram;
    private SwapChain swapChain;
    private TextureDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;
    private VulkanBuffer[] viewMatricesBuffer;
    private MatrixDescriptorSet[] viewMatricesDescriptorSets;

    public GeometryRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache, Scene scene) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        device = swapChain.getDevice();
        geometryFrameBuffer = new GeometryFrameBuffer(swapChain);
        int numImages = swapChain.getNumImages();
        createShaders();
        createDescriptorPool();
        createDescriptorSets(numImages);
        createPipeline();
        createCommandBuffers(commandPool, numImages);
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
    }

    public void cleanup() {
        pipeLine.cleanup();
        materialsBuffer.cleanup();
        Arrays.stream(viewMatricesBuffer).forEach(VulkanBuffer::cleanup);
        projMatrixUniform.cleanup();
        textureSampler.cleanup();
        materialDescriptorSetLayout.cleanup();
        textureDescriptorSetLayout.cleanup();
        matrixDescriptorSetLayout.cleanup();
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
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages) {
        matrixDescriptorSetLayout = new MatrixDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        textureDescriptorSetLayout = new TextureDescriptorSetLayout(device, 0);
        materialDescriptorSetLayout = new MaterialDescriptorSetLayout(device, 0);
        geometryDescriptorSetLayouts = new DescriptorSetLayout[]{
                matrixDescriptorSetLayout,
                matrixDescriptorSetLayout,
                textureDescriptorSetLayout,
                textureDescriptorSetLayout,
                textureDescriptorSetLayout,
                materialDescriptorSetLayout,
        };

        EngineProperties engineProps = EngineProperties.getInstance();
        descriptorSetMap = new HashMap<>();
        textureSampler = new TextureSampler(device, 1);
        projMatrixUniform = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        projMatrixDescriptorSet = new MatrixDescriptorSet(descriptorPool, matrixDescriptorSetLayout, projMatrixUniform, 0);

        viewMatricesDescriptorSets = new MatrixDescriptorSet[numImages];
        viewMatricesBuffer = new VulkanBuffer[numImages];
        materialsBuffer = new VulkanBuffer(device, (long) materialDescriptorSetLayout.getMaterialSize() * engineProps.getMaxMaterials(),
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        materialsDescriptorSet = new MaterialDescriptorSet(descriptorPool, materialDescriptorSetLayout,
                materialsBuffer, 0);
        for (int i = 0; i < numImages; i++) {
            viewMatricesBuffer[i] = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            viewMatricesDescriptorSets[i] = new MatrixDescriptorSet(descriptorPool, matrixDescriptorSetLayout,
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

    public Attachment[] getAttachments() {
        return geometryFrameBuffer.geometryAttachments().getAttachments();
    }

    public void meshUnLoaded(VulkanMesh vulkanMesh) {
        TextureDescriptorSet textureDescriptorSet = descriptorSetMap.remove(vulkanMesh.getTexture().getFileName());
        if (textureDescriptorSet != null) {
            descriptorPool.freeDescriptorSet(textureDescriptorSet.getVkDescriptorSet());
        }
        textureDescriptorSet = descriptorSetMap.remove(vulkanMesh.getNormalMapTexture().getFileName());
        if (textureDescriptorSet != null) {
            descriptorPool.freeDescriptorSet(textureDescriptorSet.getVkDescriptorSet());
        }
        textureDescriptorSet = descriptorSetMap.remove(vulkanMesh.getMetalRoughTexture().getFileName());
        if (textureDescriptorSet != null) {
            descriptorPool.freeDescriptorSet(textureDescriptorSet.getVkDescriptorSet());
        }
    }

    public void meshesLoaded(VulkanMesh[] meshes) {
        device.waitIdle();
        int meshCount = 0;
        for (VulkanMesh vulkanMesh : meshes) {
            int materialOffset = meshCount * materialDescriptorSetLayout.getMaterialSize();
            updateTextureDescriptorSet(vulkanMesh.getTexture());
            updateTextureDescriptorSet(vulkanMesh.getNormalMapTexture());
            updateTextureDescriptorSet(vulkanMesh.getMetalRoughTexture());
            updateMaterial(materialsBuffer, vulkanMesh.getMaterial(), materialOffset);
            meshCount++;
        }
    }

    public void recordCommandBuffers(List<VulkanMesh> meshes, Scene scene) {
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
            Attachment[] attachments = geometryFrameBuffer.geometryAttachments().getAttachments();
            int numAttachments = attachments.length;
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(numAttachments, stack);
            for (int i = 0; i < numAttachments; i++) {
                if (attachments[i].isDepthAttachment()) {
                    clearValues.apply(i, v -> v.depthStencil().depth(1.0f));
                } else {
                    clearValues.apply(i, v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));
                }
            }

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(geometryFrameBuffer.getRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            commandBuffer.beginRecording();
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
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

            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            LongBuffer vertexBuffer = stack.mallocLong(1);
            ByteBuffer pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE);
            LongBuffer descriptorSets = stack.mallocLong(6)
                    .put(0, projMatrixDescriptorSet.getVkDescriptorSet())
                    .put(1, viewMatricesDescriptorSets[idx].getVkDescriptorSet())
                    .put(5, materialsDescriptorSet.getVkDescriptorSet());
            VulkanUtils.copyMatrixToBuffer(viewMatricesBuffer[idx], scene.getCamera().getViewMatrix());
            IntBuffer dynDescrSetOffset = stack.callocInt(1);
            int meshCount = 0;
            for (VulkanMesh mesh : meshes) {
                int materialOffset = meshCount * materialDescriptorSetLayout.getMaterialSize();
                dynDescrSetOffset.put(0, materialOffset);
                vertexBuffer.put(0, mesh.getVerticesBuffer().getBuffer());
                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, mesh.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                TextureDescriptorSet textureDescriptorSet = descriptorSetMap.get(mesh.getTexture().getFileName());
                TextureDescriptorSet normalMapDescriptorSet = descriptorSetMap.get(mesh.getNormalMapTexture().getFileName());
                TextureDescriptorSet metalRoughDescriptorSet = descriptorSetMap.get(mesh.getMetalRoughTexture().getFileName());
                List<Entity> entities = scene.getEntitiesByMeshId(mesh.getId());
                for (Entity entity : entities) {
                    descriptorSets.put(2, textureDescriptorSet.getVkDescriptorSet());
                    descriptorSets.put(3, normalMapDescriptorSet.getVkDescriptorSet());
                    descriptorSets.put(4, metalRoughDescriptorSet.getVkDescriptorSet());
                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeLine.getVkPipelineLayout(), 0, descriptorSets, dynDescrSetOffset);

                    setPushConstants(cmdHandle, entity.getModelMatrix(), pushConstantBuffer);
                    vkCmdDrawIndexed(cmdHandle, mesh.getIndicesCount(), 1, 0, 0, 0);
                }

                meshCount++;
            }

            vkCmdEndRenderPass(cmdHandle);
            commandBuffer.endRecording();
        }
    }

    public void resize(SwapChain swapChain, Scene scene) {
        VulkanUtils.copyMatrixToBuffer(projMatrixUniform, scene.getProjection().getProjectionMatrix());
        this.swapChain = swapChain;
        geometryFrameBuffer.resize(swapChain);
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f modelMatrix, ByteBuffer pushConstantBuffer) {
        modelMatrix.get(0, pushConstantBuffer);
        vkCmdPushConstants(cmdHandle, pipeLine.getVkPipelineLayout(),
                VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
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

    private void updateMaterial(VulkanBuffer vulkanBuffer, Material material, int offset) {
        long mappedMemory = vulkanBuffer.map();
        ByteBuffer materialBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) vulkanBuffer.getRequestedSize());
        material.getDiffuseColor().get(offset, materialBuffer);
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 4, material.hasTexture() ? 1.0f : 0.0f);
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 5, material.hasNormalMap() ? 1.0f : 0.0f);
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 6, material.hasMetalRoughMap() ? 1.0f : 0.0f);
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 7, material.getRoughnessFactor());
        materialBuffer.putFloat(offset + GraphConstants.FLOAT_LENGTH * 8, material.getMetallicFactor());
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