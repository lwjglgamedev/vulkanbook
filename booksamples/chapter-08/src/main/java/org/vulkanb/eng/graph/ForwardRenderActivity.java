package org.vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

public class ForwardRenderActivity {

    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/fwd_fragment.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final int MAX_DESCRIPTORS = 100;
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/fwd_vertex.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";
    private CommandBuffer[] commandBuffers;
    private ImageView[] depthImageViews;
    private Image[] depthImages;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Map<String, TextureDescriptorSet> descriptorSetMap;
    private Fence[] fences;
    private FrameBuffer[] frameBuffers;
    private ShaderProgram fwdShaderProgram;
    private Pipeline pipeLine;
    private PipelineCache pipelineCache;
    private SwapChainRenderPass renderPass;
    private SwapChain swapChain;
    private TextureDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;

    public ForwardRenderActivity(SwapChain swapChain, CommandPool commandPool, PipelineCache pipelineCache) {
        this.swapChain = swapChain;
        this.pipelineCache = pipelineCache;
        Device device = swapChain.getDevice();

        int numImages = swapChain.getImageViews().length;
        createDepthImages();
        this.renderPass = new SwapChainRenderPass(swapChain, this.depthImages[0].getFormat());
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

        this.textureDescriptorSetLayout = new TextureDescriptorSetLayout(device, 0);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                this.textureDescriptorSetLayout,
        };

        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                this.renderPass.getVkRenderPass(), this.fwdShaderProgram, 1, true, GraphConstants.MAT4X4_SIZE * 2,
                new VertexBufferStructure(), this.descriptorSetLayouts);
        this.pipeLine = new Pipeline(this.pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanUp();

        this.commandBuffers = new CommandBuffer[numImages];
        this.fences = new Fence[numImages];
        for (int i = 0; i < numImages; i++) {
            this.commandBuffers[i] = new CommandBuffer(commandPool, true, false);
            this.fences[i] = new Fence(device, true);
        }
        this.descriptorPool = new DescriptorPool(device, MAX_DESCRIPTORS, 0);
        this.descriptorSetMap = new HashMap<>();
        this.textureSampler = new TextureSampler(device, 1);
    }

    public void cleanUp() {
        this.textureSampler.cleanUp();
        this.descriptorPool.cleanUp();
        this.pipeLine.cleanUp();
        Arrays.stream(this.descriptorSetLayouts).forEach(DescriptorSetLayout::cleanUp);
        Arrays.stream(this.depthImageViews).forEach(ImageView::cleanUp);
        Arrays.stream(this.depthImages).forEach(Image::cleanUp);
        this.fwdShaderProgram.cleanUp();
        Arrays.stream(this.frameBuffers).forEach(FrameBuffer::cleanUp);
        this.renderPass.cleanUp();
        Arrays.stream(this.commandBuffers).forEach(CommandBuffer::cleanUp);
        Arrays.stream(this.fences).forEach(Fence::cleanUp);
    }

    private void createDepthImages() {
        Device device = this.swapChain.getDevice();
        int numImages = this.swapChain.getNumImages();
        VkExtent2D swapChainExtent = this.swapChain.getSwapChainExtent();
        int mipLevels = 1;
        this.depthImages = new Image[numImages];
        this.depthImageViews = new ImageView[numImages];
        for (int i = 0; i < numImages; i++) {
            this.depthImages[i] = new Image(device, swapChainExtent.width(), swapChainExtent.height(),
                    VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, 1, mipLevels);
            this.depthImageViews[i] = new ImageView(device, this.depthImages[i].getVkImage(),
                    this.depthImages[i].getFormat(), VK_IMAGE_ASPECT_DEPTH_BIT, mipLevels);
        }
    }

    private void createFrameBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Device device = swapChain.getDevice();
            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            ImageView[] imageViews = swapChain.getImageViews();
            int numImages = imageViews.length;

            LongBuffer pAttachments = stack.mallocLong(2);
            this.frameBuffers = new FrameBuffer[numImages];
            for (int i = 0; i < numImages; i++) {
                pAttachments.put(0, imageViews[i].getVkImageView());
                pAttachments.put(1, this.depthImageViews[i].getVkImageView());
                this.frameBuffers[i] = new FrameBuffer(device, swapChainExtent.width(), swapChainExtent.height(),
                        pAttachments, this.renderPass.getVkRenderPass());
            }
        }
    }

    public void meshUnLoaded(VulkanMesh vulkanMesh) {
        this.descriptorSetMap.remove(vulkanMesh.getTextureId());
    }

    // TODO: BINDING
    public void meshesLoaded(VulkanMesh[] meshes, TextureCache textureCache) {
        for (VulkanMesh vulkanMesh : meshes) {
            TextureDescriptorSet textureDescriptorSet = this.descriptorSetMap.get(vulkanMesh.getTextureId());
            if (textureDescriptorSet == null) {
                Texture texture = textureCache.getTexture(vulkanMesh.getTextureId());
                textureDescriptorSet = new TextureDescriptorSet(this.descriptorPool, this.textureDescriptorSetLayout,
                        texture, this.textureSampler, 0);
                this.descriptorSetMap.put(vulkanMesh.getTextureId(), textureDescriptorSet);
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
            clearValues.apply(1, v -> v.depthStencil().depth(1.0f));

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

                TextureDescriptorSet textureDescriptorSet = this.descriptorSetMap.get(mesh.getTextureId());
                List<Entity> entities = scene.getEntitiesByMeshId(mesh.getId());
                for (Entity entity : entities) {
                    LongBuffer descriptorSets = stack.mallocLong(1)
                            .put(0, textureDescriptorSet.getVkDescriptorSet());
                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            this.pipeLine.getVkPipelineLayout(), 0, descriptorSets, null);

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
        for (ImageView imageView : this.depthImageViews) {
            imageView.cleanUp();
        }
        for (Image image : this.depthImages) {
            image.cleanUp();
        }
        createDepthImages();
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