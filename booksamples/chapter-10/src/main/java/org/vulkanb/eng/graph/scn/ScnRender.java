package org.vulkanb.eng.graph.scn;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR;
import static org.lwjgl.vulkan.VK13.*;

public class ScnRender {

    private static final int DEPTH_FORMAT = VK_FORMAT_D16_UNORM;
    private static final String DESC_ID_MAT = "SCN_DESC_ID_MAT";
    private static final String DESC_ID_PRJ = "SCN_DESC_ID_PRJ";
    private static final String DESC_ID_TEXT = "SCN_DESC_ID_TEXT";
    private static final String DESC_ID_VIEW = "SCN_DESC_ID_VIEW";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/scn_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final int PUSH_CONSTANTS_SIZE = VkUtils.MAT4X4_SIZE + VkUtils.INT_SIZE;
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
    private VkRenderingAttachmentInfo.Buffer[] colorAttachmentsInfo;
    private Attachment[] depthAttachments;
    private VkRenderingAttachmentInfo[] depthAttachmentsInfo;
    private VkRenderingInfo[] renderInfo;

    public ScnRender(VkCtx vkCtx, EngCtx engCtx) {
        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));
        clrValueDepth = VkClearValue.calloc();
        clrValueDepth.color(c -> c.float32(0, 1.0f));
        pushConstBuff = MemoryUtil.memAlloc(PUSH_CONSTANTS_SIZE);
        depthAttachments = createDepthAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        depthAttachmentsInfo = createDepthAttachmentsInfo(vkCtx, depthAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentsInfo);

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
                descLayoutFrgStorage, textDescSetLayout});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static VkRenderingAttachmentInfo.Buffer[] createColorAttachmentsInfo(VkCtx vkCtx, VkClearValue clearValue) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingAttachmentInfo.Buffer[numImages];

        for (int i = 0; i < numImages; ++i) {
            var attachments = VkRenderingAttachmentInfo.calloc(1);
            attachments.get(0)
                    .sType$Default()
                    .imageView(swapChain.getImageView(i).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);
            result[i] = attachments;
        }
        return result;
    }

    private static Attachment[] createDepthAttachments(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        Attachment[] depthAttachments = new Attachment[numImages];
        for (int i = 0; i < numImages; i++) {
            depthAttachments[i] = new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                    DEPTH_FORMAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        }

        return depthAttachments;
    }

    private static VkRenderingAttachmentInfo[] createDepthAttachmentsInfo(VkCtx vkCtx, Attachment[] depthAttachments, VkClearValue clearValue) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingAttachmentInfo[numImages];

        for (int i = 0; i < numImages; ++i) {
            var attachments = VkRenderingAttachmentInfo.calloc()
                    .sType$Default()
                    .imageView(depthAttachments[i].getImageView().getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);
            result[i] = attachments;
        }
        return result;
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new VtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(),
                vkCtx.getSurface().getSurfaceFormat().imageFormat())
                .setDepthFormat(DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, PUSH_CONSTANTS_SIZE)
                        })
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        Pipeline pipeLine = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeLine;
    }

    private static VkRenderingInfo[] createRenderInfo(VkCtx vkCtx, VkRenderingAttachmentInfo.Buffer[] colorAttachments,
                                                      VkRenderingAttachmentInfo[] depthAttachments) {
        SwapChain swapChain = vkCtx.getSwapChain();
        int numImages = swapChain.getNumImages();
        var result = new VkRenderingInfo[numImages];

        try (var stack = MemoryStack.stackPush()) {
            VkExtent2D extent = swapChain.getSwapChainExtent();
            var renderArea = VkRect2D.calloc(stack).extent(extent);

            for (int i = 0; i < numImages; ++i) {
                var renderingInfo = VkRenderingInfo.calloc()
                        .sType$Default()
                        .renderArea(renderArea)
                        .layerCount(1)
                        .pColorAttachments(colorAttachments[i])
                        .pDepthAttachment(depthAttachments[i]);
                result[i] = renderingInfo;
            }
        }
        return result;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV),
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
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(depthAttachmentsInfo).forEach(VkRenderingAttachmentInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        Arrays.asList(depthAttachments).forEach(a -> a.cleanup(vkCtx));
        MemoryUtil.memFree(pushConstBuff);
        clrValueDepth.free();
        clrValueColor.free();
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

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache,
                       MaterialsCache materialsCache, int imageIndex, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();

            long swapChainImage = swapChain.getImageView(imageIndex).getVkImage();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);
            VkUtils.imageBarrier(stack, cmdHandle, depthAttachments[imageIndex].getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

            vkCmdBeginRendering(cmdHandle, renderInfo[imageIndex]);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
            int width = swapChainExtent.width();
            int height = swapChainExtent.height();
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
                    .put(2, descAllocator.getDescSet(DESC_ID_MAT).getVkDescriptorSet())
                    .put(3, descAllocator.getDescSet(DESC_ID_TEXT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(),
                    0, descriptorSets, null);

            renderEntities(engCtx, cmdHandle, modelsCache, materialsCache, false);
            renderEntities(engCtx, cmdHandle, modelsCache, materialsCache, true);

            vkCmdEndRendering(cmdHandle);

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }

    private void renderEntities(EngCtx engCtx, VkCommandBuffer cmdHandle, ModelsCache modelsCache,
                                MaterialsCache materialsCache, boolean transparent) {
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer vertexBuffer = stack.mallocLong(1);
            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);

            Scene scene = engCtx.scene();
            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();
            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                VulkanModel model = modelsCache.getModel(entity.getModelId());
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    String materialId = vulkanMesh.materialdId();
                    int materialIdx = materialsCache.getPosition(materialId);
                    VulkanMaterial vulkanMaterial = materialsCache.getMaterial(materialId);
                    if (vulkanMaterial == null) {
                        Logger.warn("Mesh [{}] in model [{}] does not have material", j, model.getId());
                        continue;
                    }
                    if (vulkanMaterial.isTransparent() == transparent) {
                        setPushConstants(cmdHandle, entity.getModelMatrix(), materialIdx);
                        vertexBuffer.put(0, vulkanMesh.verticesBuffer().getBuffer());
                        vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                        vkCmdBindIndexBuffer(cmdHandle, vulkanMesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                        vkCmdDrawIndexed(cmdHandle, vulkanMesh.numIndices(), 1, 0, 0, 0);
                    }
                }
            }
        }
    }

    public void resize(EngCtx engCtx, VkCtx vkCtx) {
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(depthAttachmentsInfo).forEach(VkRenderingAttachmentInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        Arrays.asList(depthAttachments).forEach(a -> a.cleanup(vkCtx));
        depthAttachments = createDepthAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        depthAttachmentsInfo = createDepthAttachmentsInfo(vkCtx, depthAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentsInfo);
        VkUtils.copyMatrixToBuffer(vkCtx, projMatrixBuff, engCtx.scene().getProjection().getProjectionMatrix(), 0);
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f modelMatrix, int materialIdx) {
        modelMatrix.get(0, pushConstBuff);
        pushConstBuff.putInt(VkUtils.MAT4X4_SIZE, materialIdx);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
}
