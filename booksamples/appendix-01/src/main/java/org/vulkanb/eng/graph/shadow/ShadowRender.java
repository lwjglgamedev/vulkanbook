package org.vulkanb.eng.graph.shadow;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class ShadowRender {

    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
    private static final int ATT_FORMAT = VK_FORMAT_R32G32_SFLOAT;
    private static final String DESC_ID_MAT = "SHADOW_DESC_ID_MAT";
    private static final String DESC_ID_PRJ = "SHADOW_DESC_ID_PRJ";
    private static final String DESC_ID_TEXT = "SHADOW_SCN_DESC_ID_TEXT";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/shadow_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.PTR_SIZE * 2;
    private static final String SHADOW_GEOMETRY_SHADER_FILE_GLSL = "resources/shaders/shadow_geom.glsl";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_SPV = SHADOW_GEOMETRY_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final CascadeShadows[] cascadeShadows;
    private final VkClearValue clrValueColor;
    private final VkClearValue clrValueDepth;
    private final Attachment colorAttachment;
    private final VkRenderingAttachmentInfo.Buffer colorAttachmentInfo;
    private final Attachment depthAttachment;
    private final VkRenderingAttachmentInfo depthAttachmentInfo;
    private final DescSetLayout descLayoutFrgStorage;
    private final Pipeline pipeline;
    private final VkBuffer[] prjBuffers;
    private final ByteBuffer pushConstBuff;
    private final VkRenderingInfo renderingInfo;
    private final DescSetLayout textDescSetLayout;
    private final TextureSampler textureSampler;
    private final DescSetLayout uniformGeomDescSetLayout;

    public ShadowRender(VkCtx vkCtx) {
        clrValueDepth = VkClearValue.calloc();
        clrValueDepth.color(c -> c.float32(0, 1.0f));

        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 1.0f).float32(1, 1.0f));

        depthAttachment = createDepthAttachment(vkCtx);
        depthAttachmentInfo = createDepthAttachmentInfo(depthAttachment, clrValueDepth);

        colorAttachment = createColorAttachment(vkCtx);
        colorAttachmentInfo = createColorAttachmentInfo(colorAttachment, clrValueColor);

        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);

        renderingInfo = createRenderInfo(colorAttachmentInfo, depthAttachmentInfo);
        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        uniformGeomDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                0, 1, VK_SHADER_STAGE_GEOMETRY_BIT));
        long buffSize = (long) VkUtils.MAT4X4_SIZE * Scene.SHADOW_MAP_CASCADE_COUNT;
        prjBuffers = VkUtils.createHostVisibleBuffs(vkCtx, buffSize, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, DESC_ID_PRJ, uniformGeomDescSetLayout);

        descLayoutFrgStorage = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        textDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, TextureCache.MAX_TEXTURES, VK_SHADER_STAGE_FRAGMENT_BIT));

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{uniformGeomDescSetLayout, textDescSetLayout,
                descLayoutFrgStorage});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));

        cascadeShadows = new CascadeShadows[VkUtils.MAX_IN_FLIGHT];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            cascadeShadows[i] = new CascadeShadows();
        }
    }

    private static Attachment createColorAttachment(VkCtx vkCtx) {
        int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
        return new Attachment(vkCtx, shadowMapSize, shadowMapSize,
                ATT_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, Scene.SHADOW_MAP_CASCADE_COUNT);
    }

    private static VkRenderingAttachmentInfo.Buffer createColorAttachmentInfo(Attachment srcAttachment, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc(1)
                .sType$Default()
                .imageView(srcAttachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }

    private static Attachment createDepthAttachment(VkCtx vkCtx) {
        int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
        return new Attachment(vkCtx, shadowMapSize, shadowMapSize,
                DEPTH_FORMAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, Scene.SHADOW_MAP_CASCADE_COUNT);
    }

    private static VkRenderingAttachmentInfo createDepthAttachmentInfo(Attachment depthAttachment, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc()
                .sType$Default()
                .imageView(depthAttachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .clearValue(clearValue);
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{ATT_FORMAT})
                .setDepthFormat(DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, PUSH_CONSTANTS_SIZE)
                        })
                .setDescSetLayouts(descSetLayouts)
                .setDescSetLayouts(descSetLayouts)
                .setDepthClamp(vkCtx.getDevice().getDepthClamp());
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo createRenderInfo(VkRenderingAttachmentInfo.Buffer colorAttachmentInfo,
                                                    VkRenderingAttachmentInfo depthAttachments) {
        var result = VkRenderingInfo.calloc().sType$Default();
        try (var stack = MemoryStack.stackPush()) {
            int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
            VkExtent2D extent = VkExtent2D.calloc(stack);
            extent.width(shadowMapSize);
            extent.height(shadowMapSize);
            var renderArea = VkRect2D.calloc(stack).extent(extent);
            result.renderArea(renderArea)
                    .layerCount(Scene.SHADOW_MAP_CASCADE_COUNT)
                    .pColorAttachments(colorAttachmentInfo)
                    .pDepthAttachment(depthAttachments);
        }
        return result;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(SHADOW_GEOMETRY_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_geometry_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_GEOMETRY_BIT, SHADOW_GEOMETRY_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV, null),
        };
    }

    public void cleanup(VkCtx vkCtx) {
        pipeline.cleanup(vkCtx);
        uniformGeomDescSetLayout.cleanup(vkCtx);
        descLayoutFrgStorage.cleanup(vkCtx);
        textDescSetLayout.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        Arrays.asList(prjBuffers).forEach(b -> b.cleanup(vkCtx));
        renderingInfo.free();
        depthAttachmentInfo.free();
        depthAttachment.cleanup(vkCtx);
        colorAttachmentInfo.free();
        colorAttachment.cleanup(vkCtx);
        MemoryUtil.memFree(pushConstBuff);
        clrValueColor.free();
        clrValueDepth.free();
    }

    public CascadeShadows getCascadeShadows(int currentFrame) {
        return cascadeShadows[currentFrame];
    }

    public Attachment getShadowAttachment() {
        return colorAttachment;
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
            Scene scene = engCtx.scene();

            ShadowUtils.updateCascadeShadows(cascadeShadows[currentFrame], scene);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, colorAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);
            VkUtils.imageBarrier(stack, cmdHandle, depthAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

            vkCmdBeginRendering(cmdHandle, renderingInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
            int width = shadowMapSize;
            int height = shadowMapSize;
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

            updateProjBuffer(vkCtx, currentFrame);
            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ, currentFrame).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            setPushConstants(cmdHandle, globalBuffers, currentFrame);

            vkCmdDrawIndirect(cmdHandle, globalBuffers.getIndirectBuffer(currentFrame).getBuffer(), 0,
                    globalBuffers.getDrawCount(currentFrame), GlobalBuffers.IND_COMMAND_STRIDE);

            vkCmdEndRendering(cmdHandle);
        }
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, GlobalBuffers globalBuffers, int currentFrame) {
        int offset = 0;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufInstanceData(currentFrame));
        offset += VkUtils.PTR_SIZE;
        pushConstBuff.putLong(offset, globalBuffers.getAddrBufModeMatrices(currentFrame));
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }

    private void updateProjBuffer(VkCtx vkCtx, int currentFrame) {
        int offset = 0;
        List<CascadeData> cascadeDataList = cascadeShadows[currentFrame].getCascadeData();
        int numCascades = cascadeDataList.size();
        VkBuffer vkBuffer = prjBuffers[currentFrame];
        long mappedMemory = vkBuffer.map(vkCtx);
        ByteBuffer buff = MemoryUtil.memByteBuffer(mappedMemory, (int) vkBuffer.getRequestedSize());
        for (int i = 0; i < numCascades; i++) {
            CascadeData cascadeData = cascadeDataList.get(i);
            cascadeData.getProjViewMatrix().get(offset, buff);
            offset += VkUtils.MAT4X4_SIZE;
        }
        vkBuffer.unMap(vkCtx);
    }
}