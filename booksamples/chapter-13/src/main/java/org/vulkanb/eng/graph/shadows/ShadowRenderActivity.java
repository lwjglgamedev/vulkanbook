package org.vulkanb.eng.graph.shadows;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.geometry.GeometryAttachments;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class ShadowRenderActivity {

    private static final String SHADOW_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/shadow_fragment.glsl";
    private static final String SHADOW_FRAGMENT_SHADER_FILE_SPV = SHADOW_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String SHADOW_VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vertex.glsl";
    private static final String SHADOW_VERTEX_SHADER_FILE_SPV = SHADOW_VERTEX_SHADER_FILE_GLSL + ".spv";
    private List<CascadeShadow> cascadeShadows;
    private CommandBuffer[] commandBuffers;
    private Attachment depthAttachment;
    private Image depthImage;
    private ImageView depthImageView;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Device device;
    private Fence[] fences;
    private List<Pipeline> pipeLines;
    private DescriptorSet.UniformDescriptorSet[] projMatrixDescriptorSet;
    private ShaderProgram shaderProgram;
    private ShadowSpecConstant shadowSpecConstant;
    private List<ShadowsFrameBuffer> shadowsFrameBuffers;
    private VulkanBuffer[] shadowsUniforms;
    private SwapChain swapChain;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public ShadowRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache) {
        this.swapChain = swapChain;
        device = swapChain.getDevice();
        int numImages = swapChain.getNumImages();
        shadowSpecConstant = new ShadowSpecConstant();
        createAttachment();
        createFrameBuffers(device);
        createShaders();
        createDescriptorPool(numImages);
        createDescriptorSets(numImages);
        createPipelines(pipelineCache);
        createCommandBuffers(commandPool, numImages);
        createShadowCascades();
    }

    private static void setPushConstant(Pipeline pipeLine, VkCommandBuffer cmdHandle, Matrix4f matrix, int cascade) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH);
            matrix.get(0, pushConstantBuffer);
            pushConstantBuffer.putInt(GraphConstants.MAT4X4_SIZE, cascade);
            vkCmdPushConstants(cmdHandle, pipeLine.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
        }
    }

    public void cleanup() {
        pipeLines.stream().forEach(Pipeline::cleanup);
        Arrays.stream(shadowsUniforms).forEach(VulkanBuffer::cleanup);
        uniformDescriptorSetLayout.cleanup();
        descriptorPool.cleanup();
        shadowSpecConstant.cleanup();
        shaderProgram.cleanup();
        shadowsFrameBuffers.stream().forEach(ShadowsFrameBuffer::cleanup);
        depthAttachment.cleanup();
        Arrays.stream(commandBuffers).forEach(CommandBuffer::cleanup);
        Arrays.stream(fences).forEach(Fence::cleanup);
    }

    private void createAttachment() {
        int mipLevels = 1;
        int sampleCount = 1;
        int usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        EngineProperties engineProperties = EngineProperties.getInstance();
        int shadowMapSize = engineProperties.getShadowMapSize();
        depthImage = new Image(device, shadowMapSize, shadowMapSize,
                VK_FORMAT_D32_SFLOAT, usage | VK_IMAGE_USAGE_SAMPLED_BIT, mipLevels, sampleCount,
                GraphConstants.SHADOW_MAP_CASCADE_COUNT);

        int aspectMask = Attachment.calcAspectMask(usage);

        depthImageView = new ImageView(device, depthImage.getVkImage(), depthImage.getFormat(), aspectMask, 1,
                VK_IMAGE_VIEW_TYPE_2D_ARRAY, 0);
        depthAttachment = new Attachment(depthImage, depthImageView, true);
    }

    private void createCommandBuffers(CommandPool commandPool, int numImages) {
        commandBuffers = new CommandBuffer[numImages];
        fences = new Fence[numImages];

        for (int i = 0; i < numImages; i++) {
            commandBuffers[i] = new CommandBuffer(commandPool, true, false);
            fences[i] = new Fence(device, true);
        }
    }

    private void createDescriptorPool(int numImages) {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(numImages, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(int numImages) {
        uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(device, 0, VK_SHADER_STAGE_VERTEX_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
        };

        projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet[numImages];
        shadowsUniforms = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            shadowsUniforms[i] = new VulkanBuffer(device,
                    GraphConstants.MAT4X4_SIZE * GraphConstants.SHADOW_MAP_CASCADE_COUNT,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            projMatrixDescriptorSet[i] = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout,
                    shadowsUniforms[i], 0);
        }
    }

    private void createFrameBuffers(Device device) {
        shadowsFrameBuffers = new ArrayList<>();
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            ShadowsFrameBuffer shadowsFrameBuffer = new ShadowsFrameBuffer(device, depthImage, depthImageView, i);
            shadowsFrameBuffers.add(shadowsFrameBuffer);
        }
    }

    private void createPipelines(PipelineCache pipelineCache) {
        pipeLines = new ArrayList<>();
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                    shadowsFrameBuffers.get(i).getRenderPass().getVkRenderPass(), shaderProgram, GeometryAttachments.NUMBER_COLOR_ATTACHMENTS,
                    true, true, GraphConstants.MAT4X4_SIZE + GraphConstants.INT_LENGTH,
                    new VertexBufferStructure(), descriptorSetLayouts);
            Pipeline pipeLine = new Pipeline(pipelineCache, pipeLineCreationInfo);
            pipeLines.add(pipeLine);
            pipeLineCreationInfo.cleanup();
        }
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(SHADOW_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(SHADOW_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, SHADOW_VERTEX_SHADER_FILE_SPV,
                                shadowSpecConstant.getSpecInfo()),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, SHADOW_FRAGMENT_SHADER_FILE_SPV),
                });
    }

    private void createShadowCascades() {
        EngineProperties engineProperties = EngineProperties.getInstance();

        float zNear = engineProperties.getZNear();
        float zFar = engineProperties.getZFar();
        float[] cascadeSplits = new float[]{zFar / 20.0f, zFar / 10.0f, zFar};
        cascadeShadows = new ArrayList<>();
        for (int i = 0; i < GraphConstants.SHADOW_MAP_CASCADE_COUNT; i++) {
            CascadeShadow cascadeShadow = new CascadeShadow(zNear, cascadeSplits[i]);
            cascadeShadows.add(cascadeShadow);
            zNear = cascadeSplits[i];
        }
    }

    public Attachment getAttachment() {
        return depthAttachment;
    }

    public List<CascadeShadow> getShadowCascades() {
        return cascadeShadows;
    }

    public void recordCommandBuffers(SwapChain swapChain, List<VulkanMesh> meshList, Scene scene) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (scene.isLightChanged() || scene.getCamera().isHasMoved()) {
                updateCascadeShadows(scene);
            }

            int idx = swapChain.getCurrentFrame();
            Fence fence = fences[idx];
            CommandBuffer commandBuffer = commandBuffers[idx];

            fence.fenceWait();
            fence.reset();

            updateProjViewBuffers(idx);

            commandBuffer.reset();
            commandBuffer.beginRecording();

            VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
            clearValues.apply(0, v -> v.depthStencil().depth(1.0f));

            EngineProperties engineProperties = EngineProperties.getInstance();
            int shadowMapSize = engineProperties.getShadowMapSize();
            int width = shadowMapSize;
            int height = shadowMapSize;

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

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

            int cascade = 0;
            for (ShadowsFrameBuffer shadowsFrameBuffer : shadowsFrameBuffers) {
                FrameBuffer frameBuffer = shadowsFrameBuffer.getFrameBuffer();

                VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                        .renderPass(shadowsFrameBuffer.getRenderPass().getVkRenderPass())
                        .pClearValues(clearValues)
                        .renderArea(a -> a.extent().set(width, height))
                        .framebuffer(frameBuffer.getVkFrameBuffer());

                vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

                Pipeline pipeLine = pipeLines.get(cascade);
                vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeLine.getVkPipeline());

                LongBuffer offsets = stack.mallocLong(1);
                offsets.put(0, 0L);
                LongBuffer vertexBuffer = stack.mallocLong(1);
                LongBuffer descriptorSets = stack.mallocLong(1)
                        .put(0, projMatrixDescriptorSet[idx].getVkDescriptorSet());

                vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

                for (VulkanMesh mesh : meshList) {
                    vertexBuffer.put(0, mesh.getVerticesBuffer().getBuffer());
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    vkCmdBindIndexBuffer(cmdHandle, mesh.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                    List<Entity> entities = scene.getEntitiesByMeshId(mesh.getId());
                    for (Entity entity : entities) {
                        setPushConstant(pipeLine, cmdHandle, entity.getModelMatrix(), cascade);
                        vkCmdDrawIndexed(cmdHandle, mesh.getIndicesCount(), 1, 0, 0, 0);
                    }
                }
                vkCmdEndRenderPass(cmdHandle);
                cascade++;
            }
            commandBuffer.endRecording();
        }
    }

    public void resize(SwapChain swapChain, Scene scene) {
        this.swapChain = swapChain;
        updateCascadeShadows(scene);
    }

    // TODO: Review synchronization
    public void submit(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = swapChain.getCurrentFrame();
            CommandBuffer commandBuffer = commandBuffers[idx];
            Fence currentFence = fences[idx];
            SwapChain.SyncSemaphores syncSemaphores = swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.shadowCompleteShemaphore().getVkSemaphore()),
                    currentFence);
        }
    }

    public void updateCascadeShadows(Scene scene) {
        VkExtent2D extent = swapChain.getSwapChainExtent();
        CascadeShadow.updateCascadeShadows(extent.width(), extent.height(), cascadeShadows, scene);
    }

    private void updateProjViewBuffers(int idx) {
        int offset = 0;
        for (CascadeShadow cascadeShadow : cascadeShadows) {
            VulkanUtils.copyMatrixToBuffer(shadowsUniforms[idx], cascadeShadow.getProjViewMatrix(), offset);
            offset += GraphConstants.MAT4X4_SIZE;
        }
    }
}
