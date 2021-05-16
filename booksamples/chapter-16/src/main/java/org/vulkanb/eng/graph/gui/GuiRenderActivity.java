package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.type.ImInt;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.lighting.LightingFrameBuffer;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

public class GuiRenderActivity {

    private static final String GUI_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/gui_fragment.glsl";
    private static final String GUI_FRAGMENT_SHADER_FILE_SPV = GUI_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GUI_VERTEX_SHADER_FILE_GLSL = "resources/shaders/gui_vertex.glsl";
    private static final String GUI_VERTEX_SHADER_FILE_SPV = GUI_VERTEX_SHADER_FILE_GLSL + ".spv";

    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Device device;
    private Texture fontsTexture;
    private TextureSampler fontsTextureSampler;
    private VulkanBuffer[] indicesBuffers;
    private Pipeline pipeline;
    private ShaderProgram shaderProgram;
    private SwapChain swapChain;
    private TextureDescriptorSet textureDescriptorSet;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private VulkanBuffer[] vertexBuffers;

    public GuiRenderActivity(SwapChain swapChain, CommandPool commandPool, Queue queue, PipelineCache pipelineCache,
                             LightingFrameBuffer lightingFrameBuffer) {
        this.swapChain = swapChain;
        device = swapChain.getDevice();

        createShaders();
        createUIResources(swapChain, commandPool, queue);
        createDescriptorPool();
        createDescriptorSets();
        createPipeline(pipelineCache, lightingFrameBuffer);
    }

    public void cleanup() {
        textureDescriptorSetLayout.cleanup();
        fontsTextureSampler.cleanup();
        descriptorPool.cleanup();
        fontsTexture.cleanup();
        Arrays.stream(vertexBuffers).filter(v -> v != null).forEach(VulkanBuffer::cleanup);
        Arrays.stream(indicesBuffers).filter(v -> v != null).forEach(VulkanBuffer::cleanup);
        ImGui.destroyContext();
        pipeline.cleanup();
        shaderProgram.cleanup();
    }

    private void createDescriptorPool() {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets() {
        textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, 1, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                textureDescriptorSetLayout,
        };
        fontsTextureSampler = new TextureSampler(device, 1);
        textureDescriptorSet = new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, fontsTexture,
                fontsTextureSampler, 0);
    }

    private void createPipeline(PipelineCache pipelineCache, LightingFrameBuffer lightingFrameBuffer) {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                lightingFrameBuffer.getLightingRenderPass().getVkRenderPass(), shaderProgram, 1, false, true,
                GraphConstants.FLOAT_LENGTH * 2,
                new ImGuiVertexBufferStructure(), descriptorSetLayouts);
        pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanup();
    }

    private void createShaders() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        if (engineProperties.isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(GUI_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(GUI_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        shaderProgram = new ShaderProgram(device, new ShaderProgram.ShaderModuleData[]
                {
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_VERTEX_BIT, GUI_VERTEX_SHADER_FILE_SPV),
                        new ShaderProgram.ShaderModuleData(VK_SHADER_STAGE_FRAGMENT_BIT, GUI_FRAGMENT_SHADER_FILE_SPV),
                });
    }

    private void createUIResources(SwapChain swapChain, CommandPool commandPool, Queue queue) {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
        imGuiIO.setDisplayFramebufferScale(1.0f, 1.0f);

        ImInt texWidth = new ImInt();
        ImInt texHeight = new ImInt();
        ByteBuffer buf = imGuiIO.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
        fontsTexture = new Texture(device, buf, texWidth.get(), texHeight.get(), VK_FORMAT_R8G8B8A8_UNORM);

        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();
        fontsTexture.recordTextureTransition(cmd);
        cmd.endRecording();
        cmd.submitAndWait(device, queue);
        cmd.cleanup();

        vertexBuffers = new VulkanBuffer[swapChain.getNumImages()];
        indicesBuffers = new VulkanBuffer[swapChain.getNumImages()];
    }

    public void recordCommandBuffer(Scene scene, CommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int idx = swapChain.getCurrentFrame();

            IGuiInstance guiInstance = scene.getGuiInstance();
            if (guiInstance == null) {
                return;
            }
            guiInstance.drawGui();
            updateBuffers(idx);
            if (vertexBuffers[idx] == null) {
                return;
            }

            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            LongBuffer vtxBuffer = stack.mallocLong(1);
            vtxBuffer.put(0, vertexBuffers[idx].getBuffer());
            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            vkCmdBindVertexBuffers(cmdHandle, 0, vtxBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, indicesBuffers[idx].getBuffer(), 0, VK_INDEX_TYPE_UINT16);

            ImGuiIO io = ImGui.getIO();
            FloatBuffer pushConstantBuffer = stack.mallocFloat(2);
            pushConstantBuffer.put(0, 2.0f / io.getDisplaySizeX());
            pushConstantBuffer.put(1, -2.0f / io.getDisplaySizeY());
            vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);

            LongBuffer descriptorSets = stack.mallocLong(1)
                    .put(0, this.textureDescriptorSet.getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            ImVec4 imVec4 = new ImVec4();
            VkRect2D.Buffer rect = VkRect2D.callocStack(1, stack);
            ImDrawData imDrawData = ImGui.getDrawData();
            int numCmdLists = imDrawData.getCmdListsCount();
            int offsetIdx = 0;
            int offsetVtx = 0;
            for (int i = 0; i < numCmdLists; i++) {
                int cmdBufferSize = imDrawData.getCmdListCmdBufferSize(i);
                for (int j = 0; j < cmdBufferSize; j++) {
                    imDrawData.getCmdListCmdBufferClipRect(i, j, imVec4);
                    rect.offset(it -> it.x((int) Math.max(imVec4.x, 0)).y((int) Math.max(imVec4.y, 1)));
                    rect.extent(it -> it.width((int) (imVec4.z - imVec4.x)).height((int) (imVec4.w - imVec4.y)));
                    vkCmdSetScissor(cmdHandle, 0, rect);
                    int numElements = imDrawData.getCmdListCmdBufferElemCount(i, j);
                    vkCmdDrawIndexed(cmdHandle, numElements, 1, offsetIdx, offsetVtx, 0);
                    offsetIdx += numElements;
                }
                offsetVtx += imDrawData.getCmdListVtxBufferSize(i);
            }
        }
    }

    public void resize(SwapChain swapChain) {
        this.swapChain = swapChain;
        ImGuiIO imGuiIO = ImGui.getIO();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
    }

    private void updateBuffers(int idx) {
        ImDrawData imDrawData = ImGui.getDrawData();

        int vertexBufferSize = imDrawData.getTotalVtxCount() * ImGuiVertexBufferStructure.VERTEX_SIZE;
        int indexBufferSize = imDrawData.getTotalIdxCount() * GraphConstants.SHORT_LENGTH;

        if (vertexBufferSize == 0 || indexBufferSize == 0) {
            return;
        }
        VulkanBuffer vertexBuffer = vertexBuffers[idx];
        if (vertexBuffer == null || vertexBufferSize != vertexBuffer.getRequestedSize()) {
            if (vertexBuffer != null) {
                vertexBuffer.cleanup();
            }
            vertexBuffer = new VulkanBuffer(device, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            vertexBuffers[idx] = vertexBuffer;
        }

        VulkanBuffer indicesBuffer = indicesBuffers[idx];
        if (indicesBuffer == null || indexBufferSize != indicesBuffer.getRequestedSize()) {
            if (indicesBuffer != null) {
                indicesBuffer.cleanup();
            }
            indicesBuffer = new VulkanBuffer(device, indexBufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, 0);
            indicesBuffers[idx] = indicesBuffer;
        }

        ByteBuffer dstVertexBuffer = MemoryUtil.memByteBuffer(vertexBuffer.map(), vertexBufferSize);
        ByteBuffer dstIdxBuffer = MemoryUtil.memByteBuffer(indicesBuffer.map(), indexBufferSize);

        int numCmdLists = imDrawData.getCmdListsCount();
        for (int i = 0; i < numCmdLists; i++) {
            ByteBuffer imguiVertexBuffer = imDrawData.getCmdListVtxBufferData(i);
            dstVertexBuffer.put(imguiVertexBuffer);

            // Always get the indices buffer after finishing with the vertices buffer
            ByteBuffer imguiIndicesBuffer = imDrawData.getCmdListIdxBufferData(i);
            dstIdxBuffer.put(imguiIndicesBuffer);
        }

        vertexBuffer.flush();
        indicesBuffer.flush();

        vertexBuffer.unMap();
        indicesBuffer.unMap();
    }
}