package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngineProperties;
import org.vulkanb.eng.graph.lighting.LightingFrameBuffer;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK11.*;

// TODO: New LWJGL VERSION DOES NOT WORK WITH JAR EXECUTION
// TODO: Maven dependencies for all OSs
// TODO: Maintain y axis convention
public class GuiRenderActivity {

    private static final String GUI_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/gui_fragment.glsl";
    private static final String GUI_FRAGMENT_SHADER_FILE_SPV = GUI_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GUI_VERTEX_SHADER_FILE_GLSL = "resources/shaders/gui_vertex.glsl";
    private static final String GUI_VERTEX_SHADER_FILE_SPV = GUI_VERTEX_SHADER_FILE_GLSL + ".spv";

    private Device device;
    private SwapChain swapChain;
    private Pipeline pipeline;
    private ShaderProgram shaderProgram;
    private VulkanBuffer vertexBuffer;
    private VulkanBuffer indicesBuffer;
    private Texture fontsTexture;
    private TextureSampler fontsTextureSampler;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private TextureDescriptorSet textureDescriptorSet;
    private DescriptorPool descriptorPool;

    // TODO: Support resize
    // TODO: We need to flush buffers or we can use host coherent?
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

        // TODO: Extract to Utility class?
        // TODO: Mip Levels
        CommandBuffer cmd = new CommandBuffer(commandPool, true, true);
        cmd.beginRecording();
        fontsTexture.recordTextureTransition(cmd);
        cmd.endRecording();
        Fence fence = new Fence(device, true);
        fence.reset();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            queue.submit(stack.pointers(cmd.getVkCommandBuffer()), null, null, null, fence);
        }
        fence.fenceWait();
        fence.cleanup();
        cmd.cleanup();

        ImGui.newFrame();
        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.showDemoWindow();
        ImGui.endFrame();
        ImGui.render();
    }

    private void createDescriptorPool() {
        List<DescriptorPool.DescriptorTypeCount> descriptorTypeCounts = new ArrayList<>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        descriptorPool = new DescriptorPool(device, descriptorTypeCounts);
    }

    private void createDescriptorSets() {
        textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(device, 0, VK_SHADER_STAGE_FRAGMENT_BIT);
        descriptorSetLayouts = new DescriptorSetLayout[]{
                textureDescriptorSetLayout,
        };
        fontsTextureSampler = new TextureSampler(device, 1);
        textureDescriptorSet = new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, fontsTexture,
                fontsTextureSampler, 0);
    }

    private void updateBuffers() {
        ImDrawData imDrawData = ImGui.getDrawData();

        int vertexBufferSize = imDrawData.getTotalVtxCount() * ImGuiVertexBufferStructure.VERTEX_SIZE;
        int indexBufferSize = imDrawData.getTotalIdxCount() * GraphConstants.SHORT_LENGTH;

        // TODO: Non-coherent buffers
        if (vertexBuffer == null || vertexBufferSize != vertexBuffer.getRequestedSize()) {
            if (vertexBuffer != null) {
                vertexBuffer.cleanup();
            }
            vertexBuffer = new VulkanBuffer(device, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        if (indicesBuffer == null || indexBufferSize != indicesBuffer.getRequestedSize()) {
            if (indicesBuffer != null) {
                indicesBuffer.cleanup();
            }
            indicesBuffer = new VulkanBuffer(device, indexBufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        }

        ByteBuffer dstVertexBuffer = MemoryUtil.memByteBuffer(vertexBuffer.map(), vertexBufferSize);
        ByteBuffer dstIdxBuffer = MemoryUtil.memByteBuffer(indicesBuffer.map(), indexBufferSize);

        int numCmdLists = imDrawData.getCmdListsCount();
        for (int i = 0; i < numCmdLists; i++) {
            ByteBuffer imguiVertexBuffer = imDrawData.getCmdListVtxBufferData(i);
            dstVertexBuffer.put(imguiVertexBuffer);

            // TODO: Check why this cannot be called after getting the vertexbuffer
            ByteBuffer imguiIndicesBuffer = imDrawData.getCmdListIdxBufferData(i);
            dstIdxBuffer.put(imguiIndicesBuffer);
        }
        vertexBuffer.unMap();
        indicesBuffer.unMap();

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

    private void createPipeline(PipelineCache pipelineCache, LightingFrameBuffer lightingFrameBuffer) {
        Pipeline.PipeLineCreationInfo pipeLineCreationInfo = new Pipeline.PipeLineCreationInfo(
                lightingFrameBuffer.getLightingRenderPass().getVkRenderPass(), shaderProgram, 1, false, true,
                GraphConstants.FLOAT_LENGTH * 4,
                new ImGuiVertexBufferStructure(), descriptorSetLayouts);
        pipeline = new Pipeline(pipelineCache, pipeLineCreationInfo);
        pipeLineCreationInfo.cleanup();
    }

    public void cleanup() {
        textureDescriptorSetLayout.cleanup();
        fontsTextureSampler.cleanup();
        descriptorPool.cleanup();
        fontsTexture.cleanup();
        vertexBuffer.cleanup();
        indicesBuffer.cleanup();
        ImGui.destroyContext();
        pipeline.cleanup();
        shaderProgram.cleanup();
    }

    public void render(CommandBuffer commandBuffer) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            updateBuffers();

            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();

            VkCommandBuffer cmdHandle = commandBuffer.getVkCommandBuffer();

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            VkViewport.Buffer viewport = VkViewport.callocStack(1, stack)
                    .x(0)
                    .y(0)
                    .height(height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            LongBuffer vtxBuffer = stack.mallocLong(1);
            vtxBuffer.put(0, vertexBuffer.getBuffer());
            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            vkCmdBindVertexBuffers(cmdHandle, 0, vtxBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, indicesBuffer.getBuffer(), 0, VK_INDEX_TYPE_UINT16);

            ImGuiIO io = ImGui.getIO();
            FloatBuffer pushConstantBuffer = stack.mallocFloat(4);
            pushConstantBuffer.put(0, 2.0f / io.getDisplaySizeX());
            pushConstantBuffer.put(1, 2.0f / io.getDisplaySizeY());
            pushConstantBuffer.put(2, -1.0f);
            pushConstantBuffer.put(3, -1.0f);
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
            for (int i = 0; i < numCmdLists; i++) {
                int cmdBufferSize = imDrawData.getCmdListCmdBufferSize(i);
                for (int j = 0; j < cmdBufferSize; j++) {
                    imDrawData.getCmdListCmdBufferClipRect(i, j, imVec4);
                    rect.offset(it -> it.x((int) Math.max(imVec4.x, 0)).y((int) Math.max(imVec4.y, 1)));
                    rect.extent(it -> it.width((int) (imVec4.z - imVec4.x)).height((int) (imVec4.w - imVec4.y)));
                    vkCmdSetScissor(cmdHandle, 0, rect);
                    vkCmdDrawIndexed(cmdHandle, imDrawData.getCmdListCmdBufferElemCount(i, j), 1,
                            imDrawData.getCmdListCmdBufferIdxOffset(i, j), imDrawData.getCmdListCmdBufferVtxOffset(i, j), 0);
                }
            }
        }
    }
}
