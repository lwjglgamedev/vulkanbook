package org.vulkanb.eng.graph.shadow;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.scn.VtxBuffStruct;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class ShadowRender {

    public static final int DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
    private static final String DESC_ID_MAT = "SHADOW_DESC_ID_MAT";
    private static final String DESC_ID_PRJ = "SHADOW_DESC_ID_PRJ";
    private static final String DESC_ID_TEXT = "SHADOW_DESC_ID_TEXT";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/shadow_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_GLSL = "resources/shaders/shadow_geom.glsl";
    private static final String SHADOW_GEOMETRY_SHADER_FILE_SPV = SHADOW_GEOMETRY_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/shadow_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final CascadeShadows[] cascadeShadows;
    private final VkClearValue clrValueDepth;
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
        var engCfg = EngCfg.getInstance();
        clrValueDepth = VkClearValue.calloc();
        clrValueDepth.color(c -> c.float32(0, 1.0f));
        depthAttachment = createDepthAttachment(vkCtx);
        depthAttachmentInfo = createDepthAttachmentInfo(depthAttachment, clrValueDepth);

        pushConstBuff = MemoryUtil.memAlloc(VkUtils.MAT4X4_SIZE);

        renderingInfo = createRenderInfo(depthAttachmentInfo);
        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        uniformGeomDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                0, 1, VK_SHADER_STAGE_GEOMETRY_BIT));
        int buffSize = VkUtils.MAT4X4_SIZE * Scene.SHADOW_MAP_CASCADE_COUNT;
        prjBuffers = VkUtils.createHostVisibleBuffs(vkCtx, buffSize, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, DESC_ID_PRJ, uniformGeomDescSetLayout);

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        textDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, engCfg.getMaxTextures(), VK_SHADER_STAGE_FRAGMENT_BIT));

        descLayoutFrgStorage = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{uniformGeomDescSetLayout, textDescSetLayout,
                descLayoutFrgStorage});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));

        cascadeShadows = new CascadeShadows[VkUtils.MAX_IN_FLIGHT];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            cascadeShadows[i] = new CascadeShadows();
        }
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
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new VtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{})
                .setDepthFormat(DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.MAT4X4_SIZE)
                        })
                .setDescSetLayouts(descSetLayouts)
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true)
                .setDepthClamp(vkCtx.getDevice().getDepthClamp());
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo createRenderInfo(VkRenderingAttachmentInfo depthAttachments) {
        VkRenderingInfo result = VkRenderingInfo.calloc();
        try (var stack = MemoryStack.stackPush()) {
            int shadowMapSize = EngCfg.getInstance().getShadowMapSize();
            VkExtent2D extent = VkExtent2D.calloc(stack);
            extent.width(shadowMapSize);
            extent.height(shadowMapSize);
            var renderArea = VkRect2D.calloc(stack).extent(extent);
            result.sType$Default()
                    .renderArea(renderArea)
                    .layerCount(Scene.SHADOW_MAP_CASCADE_COUNT)
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
        textDescSetLayout.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        descLayoutFrgStorage.cleanup(vkCtx);
        Arrays.asList(prjBuffers).forEach(b -> b.cleanup(vkCtx));
        renderingInfo.free();
        depthAttachmentInfo.free();
        depthAttachment.cleanup(vkCtx);
        MemoryUtil.memFree(pushConstBuff);
        clrValueDepth.free();
    }

    public CascadeShadows getCascadeShadows(int currentFrame) {
        return cascadeShadows[currentFrame];
    }

    public Attachment getDepthAttachment() {
        return depthAttachment;
    }

    public void loadMaterials(VkCtx vkCtx, MaterialsCache materialsCache, TextureCache textureCache) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSet(device, DESC_ID_MAT, descLayoutFrgStorage);
        DescSetLayout.LayoutInfo layoutInfo = descLayoutFrgStorage.getLayoutInfo();
        var buffer = materialsCache.getMaterialsBuffer();
        descSet.setBuffer(device, buffer, buffer.getRequestedSize(), layoutInfo.binding(), layoutInfo.descType());

        int maxTextures = EngCfg.getInstance().getMaxTextures();
        List<ImageView> imageViews = textureCache.getAsList().stream().map(Texture::getImageView).toList();
        int gap = maxTextures - imageViews.size();
        if (gap > 0) {
            ImageView defaultImageView = imageViews.get(0);
            imageViews = new ArrayList<>(imageViews);
            for (int i = 0; i < gap; i++) {
                imageViews.add(defaultImageView);
            }
        } else if (gap < 0) {
            Logger.warn("Increase maximum textures to [{}]", imageViews.size());
        }
        descSet = vkCtx.getDescAllocator().addDescSet(device, DESC_ID_TEXT, textDescSetLayout);
        descSet.setImagesArr(device, imageViews, textureSampler, 0);
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, GlobalBuffers globalBuffers, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            Scene scene = engCtx.scene();

            ShadowUtils.updateCascadeShadows(cascadeShadows[currentFrame], scene);

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, depthAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

            vkCmdBeginRendering(cmdHandle, renderingInfo);

            updateProjBuffer(vkCtx, currentFrame);

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

            LongBuffer vertexBuffer = stack.mallocLong(1);
            LongBuffer instanceBuffer = stack.mallocLong(1);
            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);

            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(3)
                    .put(0, descAllocator.getDescSet(DESC_ID_PRJ, currentFrame).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet());

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            // Draw commands for non animated models
            if (globalBuffers.getNumIndirectCommands() > 0) {
                vertexBuffer.put(0, globalBuffers.getVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getInstanceDataBuffers()[currentFrame].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);

                VkBuffer indirectBuffer = globalBuffers.getIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, indirectBuffer.getBuffer(), 0, globalBuffers.getNumIndirectCommands(),
                        GlobalBuffers.IND_COMMAND_STRIDE);
            }

            if (globalBuffers.getNumAnimIndirectCommands() > 0) {
                // Draw commands for  animated models
                vertexBuffer.put(0, globalBuffers.getAnimVerticesBuffer().getBuffer());
                instanceBuffer.put(0, globalBuffers.getAnimInstanceDataBuffers()[currentFrame].getBuffer());

                vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                vkCmdBindVertexBuffers(cmdHandle, 1, instanceBuffer, offsets);
                vkCmdBindIndexBuffer(cmdHandle, globalBuffers.getIndicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                VkBuffer animIndirectBuffer = globalBuffers.getAnimIndirectBuffer();
                vkCmdDrawIndexedIndirect(cmdHandle, animIndirectBuffer.getBuffer(), 0, globalBuffers.getNumAnimIndirectCommands(),
                        GlobalBuffers.IND_COMMAND_STRIDE);
            }

            vkCmdEndRendering(cmdHandle);
        }
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
