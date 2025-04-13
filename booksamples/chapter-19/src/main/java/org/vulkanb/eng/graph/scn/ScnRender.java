package org.vulkanb.eng.graph.scn;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class ScnRender {
    private static final String DESC_ID_MAT = "SCN_DESC_ID_MAT";
    private static final String DESC_ID_PRJ = "SCN_DESC_ID_PRJ";
    private static final String DESC_ID_TEXT = "SCN_DESC_ID_TEXT";
    private static final String DESC_ID_VIEW = "SCN_DESC_ID_VIEW";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/scn_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 3;
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/scn_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final VkClearValue clrValueColor;
    private final VkClearValue clrValueDepth;
    private final DescSetLayout descLayoutFrgStorage;
    private final DescSetLayout descLayoutVtxUniform;
    private final Pipeline pipeline;
    private final VkBuffer projMatrixBuff;
    private final ByteBuffer pushConstBuff;
    private final DescSetLayout textDescSetLayout;
    private final TextureSampler textureSampler;
    private final VkBuffer[] viewMatricesBuffer;
    private VkRenderingAttachmentInfo.Buffer colorAttachmentsInfo;
    private VkRenderingAttachmentInfo depthAttachmentInfo;
    private MrtAttachments mrtAttachments;
    private VkRenderingInfo renderInfo;

    public ScnRender(VkCtx vkCtx, EngCtx engCtx) {
        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));
        clrValueDepth = VkClearValue.calloc();
        clrValueDepth.color(c -> c.float32(0, 1.0f));
        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);
        mrtAttachments = new MrtAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(mrtAttachments, clrValueColor);
        depthAttachmentInfo = createDepthAttachmentInfo(mrtAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentInfo);

        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        descLayoutVtxUniform = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                0, 1, VK_SHADER_STAGE_VERTEX_BIT));
        projMatrixBuff = VkUtils.createHostVisibleBuff(vkCtx, VkUtils.MAT4X4_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                DESC_ID_PRJ, descLayoutVtxUniform);
        VkUtils.copyMatrixToBuffer(vkCtx, projMatrixBuff, engCtx.scene().getProjection().getProjectionMatrix(), 0);

        viewMatricesBuffer = VkUtils.createHostVisibleBuffs(vkCtx, VkUtils.MAT4X4_SIZE, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, DESC_ID_VIEW, descLayoutVtxUniform);

        descLayoutFrgStorage = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        textDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, TextureCache.MAX_TEXTURES, VK_SHADER_STAGE_FRAGMENT_BIT));

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{descLayoutVtxUniform, descLayoutVtxUniform,
                textDescSetLayout, descLayoutFrgStorage});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static VkRenderingAttachmentInfo.Buffer createColorAttachmentsInfo(MrtAttachments mrtAttachments, VkClearValue clearValue) {
        List<Attachment> colorAttachments = mrtAttachments.getColorAttachments();
        int numAttachments = colorAttachments.size();
        VkRenderingAttachmentInfo.Buffer result = VkRenderingAttachmentInfo.calloc(numAttachments);
        for (int i = 0; i < numAttachments; ++i) {
            result.get(i)
                    .sType$Default()
                    .imageView(colorAttachments.get(i).getImageView().getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);
        }
        return result;
    }

    private static VkRenderingAttachmentInfo createDepthAttachmentInfo(MrtAttachments mrtAttachments, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc()
                .sType$Default()
                .imageView(mrtAttachments.getDepthAttachment().getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new VtxBuffStruct();
        int vtxPcSize = VkUtils.MAT4X4_SIZE + VkUtils.PTR_SIZE * 2;
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{
                MrtAttachments.POSITION_FORMAT, MrtAttachments.ALBEDO_FORMAT, MrtAttachments.NORMAL_FORMAT, MrtAttachments.PBR_FORMAT})
                .setDepthFormat(MrtAttachments.DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, vtxPcSize),
                                new PushConstRange(VK_SHADER_STAGE_FRAGMENT_BIT, vtxPcSize, VkUtils.INT_SIZE),
                        })
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo createRenderInfo(VkCtx vkCtx, VkRenderingAttachmentInfo.Buffer colorAttachments,
                                                    VkRenderingAttachmentInfo depthAttachments) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D extent = swapChain.getSwapChainExtent();
        VkRenderingInfo result = VkRenderingInfo.calloc();
        try (var stack = MemoryStack.stackPush()) {
            var renderArea = VkRect2D.calloc(stack).extent(extent);
            result.sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1)
                    .pColorAttachments(colorAttachments)
                    .pDepthAttachment(depthAttachments);
        }
        return result;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV, null),
        };
    }

    public void cleanup(VkCtx vkCtx) {
        pipeline.cleanup(vkCtx);
        Arrays.asList(viewMatricesBuffer).forEach(b -> b.cleanup(vkCtx));
        descLayoutFrgStorage.cleanup(vkCtx);
        textDescSetLayout.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        projMatrixBuff.cleanup(vkCtx);
        descLayoutVtxUniform.cleanup(vkCtx);
        renderInfo.free();
        depthAttachmentInfo.free();
        mrtAttachments.cleanup(vkCtx);
        colorAttachmentsInfo.free();
        MemoryUtil.memFree(pushConstBuff);
        clrValueDepth.free();
        clrValueColor.free();
    }

    public MrtAttachments getMrtAttachments() {
        return mrtAttachments;
    }

    public void loadMaterials(VkCtx vkCtx, MaterialsCache materialsCache, TextureCache textureCache) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSet(device, DESC_ID_MAT, descLayoutFrgStorage);
        DescSetLayout.LayoutInfo layoutInfo = descLayoutFrgStorage.getLayoutInfo();
        var buffer = materialsCache.getMaterialsBuffer();
        descSet.setBuffer(device, buffer, buffer.getRequestedSize(), layoutInfo.binding(), layoutInfo.descType());

        List<ImageView> imageViews = textureCache.getAsList().stream().map(Texture::getImageView).toList();
        descSet = vkCtx.getDescAllocator().addDescSet(device, DESC_ID_TEXT, textDescSetLayout);
        descSet.setImagesArr(device, imageViews, textureSampler, 0);
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, GlobalBuffers globalBuffers, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            List<Attachment> attachments = mrtAttachments.getColorAttachments();
            int numAttachments = attachments.size();
            for (int i = 0; i < numAttachments; i++) {
                Attachment attachment = attachments.get(i);
                VkUtils.imageBarrier(stack, cmdHandle, attachment.getImage().getVkImage(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
            }
            VkUtils.imageBarrier(stack, cmdHandle, mrtAttachments.getDepthAttachment().getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

            vkCmdBeginRendering(cmdHandle, renderInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            int width = mrtAttachments.getWidth();
            int height = mrtAttachments.getHeight();
            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it.width(width).height(height))
                    .offset(it -> it.x(0).y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            VkUtils.copyMatrixToBuffer(vkCtx, viewMatricesBuffer[currentFrame], engCtx.scene().getCamera().getViewMatrix(), 0);
            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_VIEW, currentFrame).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet())
                    .put(3, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            setPushConstants(cmdHandle, globalBuffers, currentFrame);

            vkCmdDrawIndirect(cmdHandle, globalBuffers.getIndirectBuffer(currentFrame).getBuffer(), 0,
                    globalBuffers.getDrawCount(currentFrame), GlobalBuffers.IND_COMMAND_STRIDE);

            vkCmdEndRendering(cmdHandle);
        }
    }

    public void resize(EngCtx engCtx, VkCtx vkCtx) {
        renderInfo.free();
        depthAttachmentInfo.free();
        mrtAttachments.cleanup(vkCtx);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);

        mrtAttachments = new MrtAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(mrtAttachments, clrValueColor);
        depthAttachmentInfo = createDepthAttachmentInfo(mrtAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentInfo);

        VkUtils.copyMatrixToBuffer(vkCtx, projMatrixBuff, engCtx.scene().getProjection().getProjectionMatrix(), 0);
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, GlobalBuffers globalBuffers, int currentFrame) {
        int offset = 0;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBuffVtxAddresses(currentFrame));
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBuffIdxAddresses(currentFrame));
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufInstanceData(currentFrame));
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
}
