package org.vulkanb.eng.graph.lighting;

import org.joml.*;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.geometry.GeometryAttachments;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class LightingRenderActivity {

    private static final String LIGHTING_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/lighting_fragment.glsl";
    private static final String LIGHTING_FRAGMENT_SHADER_FILE_SPV = LIGHTING_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String LIGHTING_VERTEX_SHADER_FILE_GLSL = "resources/shaders/lighting_vertex.glsl";
    private static final String LIGHTING_VERTEX_SHADER_FILE_SPV = LIGHTING_VERTEX_SHADER_FILE_GLSL + ".spv";

    private AttachmentsDescriptorSet attachmentsDescriptorSet;
    private AttachmentsLayout attachmentsLayout;
    private Vector4f auxVec;
    private CommandBuffer[] commandBuffers;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Device device;
    private Fence[] fences;
    private VulkanBuffer invProjBuffer;
    private MatrixDescriptorSet invProjMatrixDescriptorSet;
    private LightingFrameBuffer lightingFrameBuffer;
    private VulkanBuffer[] lightsBuffers;
    private LightsDescriptorSetLayout lightsDescriptorSetLayout;
    private LightsDescriptorSet[] lightsDescriptorSets;
    private MatrixDescriptorSetLayout matrixDescriptorSetLayout;
    private Pipeline pipeline;
    private PipelineCache pipelineCache;
    private ShaderProgram shaderProgram;
    private SpecializationConstants specializationConstants;
    private SwapChain swapChain;

    public LightingRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache,
                                  Attachment[] attachments, Scene scene) {
        this.swapChain = swapChain;
        device = swapChain.getDevice();
        this.pipelineCache = pipelineCache;
        auxVec = new Vector4f();
        specializationConstants = new SpecializationConstants();

        lightingFrameBuffer = new LightingFrameBuffer(swapChain);
        int numImages = swapChain.getNumImages();
        createShaders();
        createDescriptorPool();
        createUniforms(numImages);
        createDescriptorSets(attachments, numImages);
        createPipeline();
        createCommandBuffers(commandPool, numImages);
        updateInvProjMatrix(scene);

        for (int i = 0; i < numImages; i++) {
            preRecordCommandBuffer(i);
        }
    }

    public void cleanup() {
        matrixDescriptorSetLayout.cleanup();
        lightsDescriptorSetLayout.cleanup();
        attachmentsDescriptorSet.cleanup();
        attachmentsLayout.cleanup();
        descriptorPool.cleanup();
        Arrays.stream(lightsBuffers).forEach(VulkanBuffer::cleanup);
        pipeline.cleanup();
        specializationConstants.cleanup();
        invProjBuffer.cleanup();
        lightingFrameBuffer.cleanup();
        shaderProgram.cleanup();
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
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(GeometryAttachments.NUMBER_ATTACHMENTS, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(swapChain.getNumImages() + 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets(Attachment[] attachments, int numImages) {
        attachmentsLayout = new AttachmentsLayout(device, GeometryAttachments.NUMBER_ATTACHMENTS);
        lightsDescriptorSetLayout = new LightsDescriptorSetLayout(device);
        matrixDescriptorSetLayout = new MatrixDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                attachmentsLayout,
                lightsDescriptorSetLayout,
                matrixDescriptorSetLayout,
        };

        attachmentsDescriptorSet = new AttachmentsDescriptorSet(descriptorPool, attachmentsLayout,
                attachments, 0);
        invProjMatrixDescriptorSet = new MatrixDescriptorSet(descriptorPool, matrixDescriptorSetLayout,
                invProjBuffer, 0);

        lightsDescriptorSets = new LightsDescriptorSet[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsDescriptorSets[i] = new LightsDescriptorSet(descriptorPool, lightsDescriptorSetLayout,
                    lightsBuffers[i], 0);
        }
    }

    private void createPipeline() {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                lightingFrameBuffer.getLightingRenderPass().getVkRenderPass(), shaderProgram, 1, false, false, 0,
                new EmptyVertexBufferStructure(), descriptorSetLayouts);
        pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanup();
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(LIGHTING_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(LIGHTING_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, LIGHTING_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, LIGHTING_FRAGMENT_SHADER_FILE_SPV,
                                specializationConstants.getSpecInfo()),
                });
    }

    private void createUniforms(int numImages) {
        invProjBuffer = new VulkanBuffer(device, GraphConstants.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);

        lightsBuffers = new VulkanBuffer[numImages];
        for (int i = 0; i < numImages; i++) {
            lightsBuffers[i] = new VulkanBuffer(device, (long)
                    GraphConstants.INT_LENGTH * 4 + GraphConstants.VEC4_SIZE * 2 * GraphConstants.MAX_LIGHTS +
                    GraphConstants.VEC4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
        }
    }

    public void preRecordCommandBuffer(int idx) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();

            FrameBuffer frameBuffer = lightingFrameBuffer.getFrameBuffers()[idx];
            CommandBuffer commandBuffer = commandBuffers[idx];

            commandBuffer.reset();
            VkClearValue.Buffer clearValues = VkClearValue.callocStack(1, stack);
            clearValues.apply(0, v -> v.color().float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 1));

            VkRect2D renderArea = VkRect2D.callocStack(stack);
            renderArea.offset().set(0, 0);
            renderArea.extent().set(width, height);

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(lightingFrameBuffer.getLightingRenderPass().getVkRenderPass())
                    .pClearValues(clearValues)
                    .framebuffer(frameBuffer.getVkFrameBuffer())
                    .renderArea(renderArea);

            commandBuffer.beginRecording();
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo,
                    VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

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

            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, attachmentsDescriptorSet.getVkDescriptorSet())
                    .put(1, lightsDescriptorSets[idx].getVkDescriptorSet())
                    .put(2, invProjMatrixDescriptorSet.getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            vkCmdDraw(cmdHandle, 3, 1, 0, 0);

            vkCmdEndRenderPass(cmdHandle);
            commandBuffer.endRecording();
        }
    }

    public void prepareCommandBuffers(Scene scene) {
        int idx = swapChain.getCurrentFrame();
        Fence fence = fences[idx];

        fence.fenceWait();
        fence.reset();

        updateLights(scene.getAmbientLight(), scene.getLights(), scene.getCamera().getViewMatrix(),
                lightsBuffers[idx]);
    }

    public void resize(SwapChain swapChain, Attachment[] attachments, Scene scene) {
        this.swapChain = swapChain;
        attachmentsDescriptorSet.update(attachments);
        lightingFrameBuffer.resize(swapChain);

        updateInvProjMatrix(scene);

        int numImages = swapChain.getNumImages();
        for (int i = 0; i < numImages; i++) {
            preRecordCommandBuffer(i);
        }
    }

    public void submit(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = swapChain.getCurrentFrame();
            CommandBuffer commandBuffer = commandBuffers[idx];
            Fence currentFence = fences[idx];
            SwapChain.SyncSemaphores syncSemaphores = swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.geometryCompleteSemaphore().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphore().getVkSemaphore()),
                    currentFence);
        }
    }

    private void updateInvProjMatrix(Scene scene) {
        Matrix4f invProj = new Matrix4f(scene.getProjection().getProjectionMatrix()).invert();
        VulkanUtils.copyMatrixToBuffer(invProjBuffer, invProj);
    }

    private void updateLights(Vector4f ambientLight, Light[] lights, Matrix4f viewMatrix,
                              VulkanBuffer lightsBuffer) {
        long mappedMemory = lightsBuffer.map();
        ByteBuffer uniformBuffer = MemoryUtil.memByteBuffer(mappedMemory, (int) lightsBuffer.getRequestedSize());

        ambientLight.get(0, uniformBuffer);
        int offset = GraphConstants.VEC4_SIZE;
        int numLights = lights != null ? lights.length : 0;
        uniformBuffer.putInt(offset, numLights);
        offset += GraphConstants.VEC4_SIZE;
        for (int i = 0; i < numLights; i++) {
            Light light = lights[i];
            auxVec.set(light.getPosition());
            auxVec.mul(viewMatrix);
            auxVec.w = light.getPosition().w;
            auxVec.get(offset, uniformBuffer);
            offset += GraphConstants.VEC4_SIZE;
            light.getColor().get(offset, uniformBuffer);
            offset += GraphConstants.VEC4_SIZE;
        }

        lightsBuffer.unMap();
    }
}