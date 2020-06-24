package org.vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.List;

import static org.lwjgl.vulkan.VK10.*;

public class ForwardRenderActivity {

    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/fwd_fragment.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/fwd_vertex.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";
    private CommandBuffer[] commandBuffers;
    private Image depthImage;
    private ImageView depthImageView;
    private Fence[] fences;
    private FrameBuffer[] frameBuffers;
    private ShaderProgram fwdShaderProgram;
    private Pipeline pipeLine;
    private PipelineCache pipelineCache;
    private SwapChainRenderPass renderPass;
    private SwapChain swapChain;

    public ForwardRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        Device device = swapChain.getDevice();

        int numImages = swapChain.getImageViews().length;
        createDepthImage();
        this.renderPass = new SwapChainRenderPass(swapChain, this.depthImage);
        createFrameBuffers();

        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        this.fwdShaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV),
                });


        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                this.renderPass.getVkRenderPass(), this.fwdShaderProgram, 1, true, GraphConstants.MAT4X4_SIZE * 2,
                new VertexBufferStructure());
        this.pipeLine = new Pipeline(this.pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanUp();

        this.commandBuffers = new CommandBuffer[numImages];
        this.fences = new Fence[numImages];
        for (int i = 0; i < numImages; i++) {
            this.commandBuffers[i] = new CommandBuffer(commandPool, true, false);
            this.fences[i] = new Fence(device, true);
        }
    }

    public void cleanUp() {
        this.pipeLine.cleanUp();
        this.depthImageView.cleanUp();
        this.depthImage.cleanUp();
        this.fwdShaderProgram.cleanUp();
        for (FrameBuffer frameBuffer : this.frameBuffers) {
            frameBuffer.cleanUp();
        }
        this.renderPass.cleanUp();
        for (CommandBuffer commandBuffer : this.commandBuffers) {
            commandBuffer.cleanUp();
        }
        for (Fence fence : this.fences) {
            fence.cleanUp();
        }
    }

    private void createDepthImage() {
        Device device = this.swapChain.getDevice();
        VkExtent2D swapChainExtent = this.swapChain.getSwapChainExtent();
        int mipLevels = 1;
        this.depthImage = new Image(device, swapChainExtent.width(), swapChainExtent.height(),
                VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, 1, mipLevels);
        this.depthImageView = new ImageView(device, this.depthImage.getVkImage(),
                this.depthImage.getFormat(), VK_IMAGE_ASPECT_DEPTH_BIT, mipLevels);
    }

    private void createFrameBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = swapChain.getDevice();
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            ImageView[] imageViews = swapChain.getImageViews();
            int numImages = imageViews.length;

            LongBuffer pAttachments = stack.mallocLong(2);
            pAttachments.put(1, this.depthImageView.getVkImageView());
            this.frameBuffers = new FrameBuffer[numImages];
            for (int i = 0; i < numImages; i++) {
                pAttachments.put(0, imageViews[i].getVkImageView());
                this.frameBuffers[i] = new FrameBuffer(device, swapChainExtent.width(), swapChainExtent.height(),
                        pAttachments, this.renderPass.getVkRenderPass());
            }
        }
    }

    public void recordCommandBuffers(List<VulkanMesh> meshes, Scene scene) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D swapChainExtent = this.swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
            int idx = this.swapChain.getCurrentFrame();

            Fence fence = this.fences[idx];
            CommandBuffer commandBuffer = this.commandBuffers[idx];
            FrameBuffer frameBuffer = this.frameBuffers[idx];

            fence.fenceWait();
            fence.reset();

            VkClearValue.Buffer clearValues = VkClearValue.callocStack(2, stack);
            clearValues.apply(0, v -> v.color().float32(0, 0.5f).float32(1, 0.7f).float32(2, 0.9f).float32(3, 1));
            clearValues.apply(1, v -> v.depthStencil().depth(1.0f).stencil(0));

            VkRenderPassBeginInfo renderPassBeginInfo = VkRenderPassBeginInfo.callocStack(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(this.renderPass.getVkRenderPass())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.getVkFrameBuffer());

            commandBuffer.beginRecording();
            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();
            vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeLine.getVkPipeline());

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
            ByteBuffer pushConstantBuffer = stack.malloc(GraphConstants.MAT4X4_SIZE * 2);
            for (VulkanMesh mesh : meshes) {
                LongBuffer vertexBuffer = stack.mallocLong(1);
                vertexBuffer.put(0, mesh.getVerticesBuffer().getBuffer());
                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, mesh.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                List<Entity> entities = scene.getEntitiesByMeshId(mesh.getId());
                for (Entity entity : entities) {
                    setPushConstants(cmdHandle, scene.getPerspective().getPerspectiveMatrix(), entity.getModelMatrix(),
                            pushConstantBuffer);
                    vkCmdDrawIndexed(cmdHandle, mesh.getIndicesCount(), 1, 0, 0, 0);
                }
            }

            vkCmdEndRenderPass(cmdHandle);
            commandBuffer.endRecording();
        }
    }

    public void resize(SwapChain swapChain) {
        this.swapChain = swapChain;
        for (FrameBuffer frameBuffer : this.frameBuffers) {
            frameBuffer.cleanUp();
        }
        this.depthImageView.cleanUp();
        this.depthImage.cleanUp();

        createDepthImage();
        createFrameBuffers();
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f projMatrix, Matrix4f modelMatrix,
                                  ByteBuffer pushConstantBuffer) {
        projMatrix.get(pushConstantBuffer);
        modelMatrix.get(GraphConstants.MAT4X4_SIZE, pushConstantBuffer);
        vkCmdPushConstants(cmdHandle, this.pipeLine.getVkPipelineLayout(),
                VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
    }

    public void submit(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = this.swapChain.getCurrentFrame();
            CommandBuffer commandBuffer = this.commandBuffers[idx];
            Fence currentFence = this.fences[idx];
            SwapChain.SyncSemaphores syncSemaphores = this.swapChain.getSyncSemaphoresList()[idx];
            queue.submit(stack.pointers(commandBuffer.getVkCommandBuffer()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphores().getVkSemaphore()),
                    stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphores().getVkSemaphore()), currentFence);
        }
    }
}