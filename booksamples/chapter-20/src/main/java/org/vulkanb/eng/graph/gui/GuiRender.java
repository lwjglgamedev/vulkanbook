package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.type.ImInt;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.TextureCache;
import org.vulkanb.eng.graph.post.PostRender;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.wnd.KeyboardInput;

import java.nio.*;
import java.util.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK13.*;

public class GuiRender {
    private static final String DESC_ID_TEXT = "GUI_DESC_ID_TEXT";
    private static final String GUI_FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/gui_frg.glsl";
    private static final String GUI_FRAGMENT_SHADER_FILE_SPV = GUI_FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String GUI_VERTEX_SHADER_FILE_GLSL = "resources/shaders/gui_vtx.glsl";
    private static final String GUI_VERTEX_SHADER_FILE_SPV = GUI_VERTEX_SHADER_FILE_GLSL + ".spv";

    private final VkBuffer[] buffsIdx;
    private final VkBuffer[] buffsVtx;
    private final Texture fontsTexture;
    private final TextureSampler fontsTextureSampler;
    private final Map<Long, Long> guiTexturesMap;
    private final Pipeline pipeline;
    private final DescSetLayout textDescSetLayout;
    private VkRenderingAttachmentInfo.Buffer attInfoColor;
    private VkRenderingInfo renderInfo;

    public GuiRender(EngCtx engCtx, VkCtx vkCtx, Queue queue, Attachment dstAttachment) {
        attInfoColor = createColorAttachmentInfo(dstAttachment);
        renderInfo = createRenderInfo(dstAttachment, attInfoColor);

        ShaderModule[] shaderModules = createShaderModules(vkCtx);
        textDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{textDescSetLayout});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));

        buffsVtx = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
        buffsIdx = new VkBuffer[VkUtils.MAX_IN_FLIGHT];

        fontsTexture = initUI(vkCtx, queue);
        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        fontsTextureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        Device device = vkCtx.getDevice();
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_TEXT, 1, textDescSetLayout)[0];
        descSet.setImage(device, fontsTexture.getImageView(), fontsTextureSampler, textDescSetLayout.getLayoutInfo().binding());

        KeyboardInput ki = engCtx.window().getKeyboardInput();
        ki.setCharCallBack(new GuiUtils.CharCallBack());
        ki.addKeyCallBack(new GuiUtils.KeyCallback());

        guiTexturesMap = new HashMap<>();
    }

    private static VkRenderingAttachmentInfo.Buffer createColorAttachmentInfo(Attachment dstAttachment) {
        return VkRenderingAttachmentInfo.calloc(1)
                .sType$Default()
                .imageView(dstAttachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new GuiVtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(),
                new int[]{PostRender.COLOR_FORMAT})
                .setPushConstRanges(new PushConstRange[]{new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.VEC2_SIZE)})
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo createRenderInfo(Attachment colorAttachment, VkRenderingAttachmentInfo.Buffer colorAttachmentInfo) {
        VkRenderingInfo renderingInfo;
        try (var stack = MemoryStack.stackPush()) {
            Image image = colorAttachment.getImage();
            VkExtent2D extent = VkExtent2D.calloc(stack).width(image.getWidth()).height(image.getHeight());
            var renderArea = VkRect2D.calloc(stack).extent(extent);

            renderingInfo = VkRenderingInfo.calloc()
                    .sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1)
                    .pColorAttachments(colorAttachmentInfo);
        }
        return renderingInfo;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(GUI_VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(GUI_FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, GUI_VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, GUI_FRAGMENT_SHADER_FILE_SPV, null),
        };
    }

    private static Texture initUI(VkCtx vkCtx, Queue queue) {
        ImGui.createContext();

        ImGuiIO imGuiIO = ImGui.getIO();
        imGuiIO.setIniFilename(null);
        VkExtent2D swapChainExtent = vkCtx.getSwapChain().getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());
        imGuiIO.setDisplayFramebufferScale(1.0f, 1.0f);

        ImInt texWidth = new ImInt();
        ImInt texHeight = new ImInt();
        ByteBuffer buf = imGuiIO.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
        ImageSrc imageSrc = new ImageSrc(buf, texWidth.get(), texHeight.get(), 4);
        Texture fontsTexture = new Texture(vkCtx, "GUI_TEXTURE", imageSrc, VK_FORMAT_R8G8B8A8_SRGB);

        var cmdPool = new CmdPool(vkCtx, queue.getQueueFamilyIndex(), false);
        var cmd = new CmdBuffer(vkCtx, cmdPool, true, true);
        cmd.beginRecording();
        fontsTexture.recordTextureTransition(cmd);
        cmd.endRecording();
        cmd.submitAndWait(vkCtx, queue);
        cmd.cleanup(vkCtx, cmdPool);
        cmdPool.cleanup(vkCtx);

        return fontsTexture;
    }

    public void cleanup(VkCtx vkCtx) {
        fontsTextureSampler.cleanup(vkCtx);
        fontsTexture.cleanup(vkCtx);
        textDescSetLayout.cleanup(vkCtx);
        pipeline.cleanup(vkCtx);
        Arrays.stream(buffsVtx).filter(Objects::nonNull).forEach(b -> b.cleanup(vkCtx));
        Arrays.stream(buffsIdx).filter(Objects::nonNull).forEach(b -> b.cleanup(vkCtx));
        renderInfo.free();
        attInfoColor.free();
    }

    public void loadTextures(VkCtx vkCtx, List<GuiTexture> guiTextures, TextureCache textureCache) {
        if (guiTextures == null) {
            return;
        }
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        int numTextures = guiTextures.size();
        Device device = vkCtx.getDevice();
        for (int i = 0; i < numTextures; i++) {
            var guiTexture = guiTextures.get(i);
            String descId = guiTexture.texturePath();
            DescSet descSet = descAllocator.addDescSets(device, descId, 1, textDescSetLayout)[0];
            Texture texture = textureCache.getTexture(guiTexture.texturePath());
            descSet.setImage(device, texture.getImageView(), fontsTextureSampler, textDescSetLayout.getLayoutInfo().binding());
            guiTexturesMap.put(guiTexture.id(), descSet.getVkDescriptorSet());
        }
    }

    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, int currentFrame, Attachment dstAttachment) {
        try (var stack = MemoryStack.stackPush()) {
            updateBuffers(vkCtx, currentFrame);
            if (buffsVtx[currentFrame] == null) {
                return;
            }

            Image dstImage = dstAttachment.getImage();
            int width = dstImage.getWidth();
            int height = dstImage.getHeight();

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            vkCmdBeginRendering(cmdHandle, renderInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            LongBuffer vtxBuffer = stack.mallocLong(1);
            vtxBuffer.put(0, buffsVtx[currentFrame].getBuffer());
            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            vkCmdBindVertexBuffers(cmdHandle, 0, vtxBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, buffsIdx[currentFrame].getBuffer(), 0, VK_INDEX_TYPE_UINT16);

            ImGuiIO io = ImGui.getIO();
            FloatBuffer pushConstantBuffer = stack.mallocFloat(2);
            pushConstantBuffer.put(0, 2.0f / io.getDisplaySizeX());
            pushConstantBuffer.put(1, -2.0f / io.getDisplaySizeY());
            vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);

            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(1);

            ImVec4 imVec4 = new ImVec4();
            VkRect2D.Buffer rect = VkRect2D.calloc(1, stack);
            ImDrawData imDrawData = ImGui.getDrawData();
            int numCmdLists = imDrawData.getCmdListsCount();
            int offsetIdx = 0;
            int offsetVtx = 0;
            for (int i = 0; i < numCmdLists; i++) {
                int cmdBufferSize = imDrawData.getCmdListCmdBufferSize(i);
                for (int j = 0; j < cmdBufferSize; j++) {
                    long textDescSet;
                    long textId = imDrawData.getCmdListCmdBufferTextureId(i, j);
                    if (textId == 0) {
                        textDescSet = descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet();
                    } else {
                        textDescSet = guiTexturesMap.get(textId);
                    }
                    descriptorSets.put(0, textDescSet);
                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

                    imDrawData.getCmdListCmdBufferClipRect(imVec4, i, j);
                    rect.offset(it -> it.x((int) Math.max(imVec4.x, 0)).y((int) Math.max(imVec4.y, 1)));
                    rect.extent(it -> it.width((int) (imVec4.z - imVec4.x)).height((int) (imVec4.w - imVec4.y)));
                    vkCmdSetScissor(cmdHandle, 0, rect);
                    int numElements = imDrawData.getCmdListCmdBufferElemCount(i, j);
                    vkCmdDrawIndexed(cmdHandle, numElements, 1,
                            offsetIdx + imDrawData.getCmdListCmdBufferIdxOffset(i, j),
                            offsetVtx + imDrawData.getCmdListCmdBufferVtxOffset(i, j), 0);
                }
                offsetIdx += imDrawData.getCmdListIdxBufferSize(i);
                offsetVtx += imDrawData.getCmdListVtxBufferSize(i);
            }

            vkCmdEndRendering(cmdHandle);
        }
    }

    public void resize(VkCtx vkCtx, Attachment dstAttachment) {
        ImGuiIO imGuiIO = ImGui.getIO();
        VkExtent2D swapChainExtent = vkCtx.getSwapChain().getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());

        renderInfo.free();
        attInfoColor.free();
        attInfoColor = createColorAttachmentInfo(dstAttachment);
        renderInfo = createRenderInfo(dstAttachment, attInfoColor);
    }

    private void updateBuffers(VkCtx vkCtx, int idx) {
        ImDrawData imDrawData = ImGui.getDrawData();

        if (imDrawData.ptr == 0) {
            return;
        }
        int vertexBufferSize = imDrawData.getTotalVtxCount() * GuiVtxBuffStruct.VERTEX_SIZE;
        int indexBufferSize = imDrawData.getTotalIdxCount() * VkUtils.SHORT_LENGTH;

        if (vertexBufferSize == 0 || indexBufferSize == 0) {
            return;
        }
        var vtxBuffer = buffsVtx[idx];
        if (vtxBuffer == null || vertexBufferSize != vtxBuffer.getRequestedSize()) {
            if (vtxBuffer != null) {
                vtxBuffer.cleanup(vkCtx);
            }
            vtxBuffer = new VkBuffer(vkCtx, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            buffsVtx[idx] = vtxBuffer;
        }

        var indicesBuffer = buffsIdx[idx];
        if (indicesBuffer == null || indexBufferSize != indicesBuffer.getRequestedSize()) {
            if (indicesBuffer != null) {
                indicesBuffer.cleanup(vkCtx);
            }
            indicesBuffer = new VkBuffer(vkCtx, indexBufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            buffsIdx[idx] = indicesBuffer;
        }

        ByteBuffer dstVertexBuffer = MemoryUtil.memByteBuffer(vtxBuffer.map(vkCtx), vertexBufferSize);
        ByteBuffer dstIdxBuffer = MemoryUtil.memByteBuffer(indicesBuffer.map(vkCtx), indexBufferSize);

        int numCmdLists = imDrawData.getCmdListsCount();
        for (int i = 0; i < numCmdLists; i++) {
            ByteBuffer imguiVertexBuffer = imDrawData.getCmdListVtxBufferData(i);
            dstVertexBuffer.put(imguiVertexBuffer);

            // Always get the indices buffer after finishing with the vertices buffer
            ByteBuffer imguiIndicesBuffer = imDrawData.getCmdListIdxBufferData(i);
            dstIdxBuffer.put(imguiIndicesBuffer);
        }

        vtxBuffer.flush(vkCtx);
        indicesBuffer.flush(vkCtx);

        vtxBuffer.unMap(vkCtx);
        indicesBuffer.unMap(vkCtx);
    }
}
