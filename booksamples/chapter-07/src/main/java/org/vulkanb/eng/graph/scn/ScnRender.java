package org.vulkanb.eng.graph.scn;

import org.joml.Matrix4f;
import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
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
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/scn_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/scn_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final VkClearValue clrValueColor;
    private final VkClearValue clrValueDepth;
    private final Pipeline pipeline;
    private final ByteBuffer pushConstBuff;
    private VkRenderingAttachmentInfo.Buffer[] colorAttachmentsInfo;
    private Attachment[] depthAttachments;
    private VkRenderingAttachmentInfo[] depthAttachmentsInfo;
    private VkRenderingInfo[] renderInfo;

    public ScnRender(VkCtx vkCtx) {
        clrValueColor = VkClearValue.calloc().color(
                c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));
        clrValueDepth = VkClearValue.calloc().color(c -> c.float32(0, 1.0f));
        depthAttachments = createDepthAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        depthAttachmentsInfo = createDepthAttachmentsInfo(vkCtx, depthAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentsInfo);

        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        pushConstBuff = MemoryUtil.memAlloc(VkUtils.MAT4X4_SIZE * 2);

        pipeline = createPipeline(vkCtx, shaderModules);
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

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules) {
        var vtxBuffStruct = new VtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(),
                vkCtx.getSurface().getSurfaceFormat().imageFormat())
                .setDepthFormat(DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.MAT4X4_SIZE * 2)
                        });
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
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
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(depthAttachmentsInfo).forEach(VkRenderingAttachmentInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        Arrays.asList(depthAttachments).forEach(a -> a.cleanup(vkCtx));
        MemoryUtil.memFree(pushConstBuff);
        clrValueDepth.free();
        clrValueColor.free();
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache, int imageIndex) {
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

            LongBuffer offsets = stack.mallocLong(1).put(0, 0L);
            LongBuffer vertexBuffer = stack.mallocLong(1);

            Scene scene = engCtx.scene();
            List<Entity> entities = scene.getEntities();
            int numEntities = entities.size();
            for (int i = 0; i < numEntities; i++) {
                var entity = entities.get(i);
                VulkanModel model = modelsCache.getModel(entity.getModelId());
                List<VulkanMesh> vulkanMeshList = model.getVulkanMeshList();
                int numMeshes = vulkanMeshList.size();
                setPushConstants(cmdHandle, scene.getProjection().getProjectionMatrix(), entity.getModelMatrix());
                for (int j = 0; j < numMeshes; j++) {
                    var vulkanMesh = vulkanMeshList.get(j);
                    vertexBuffer.put(0, vulkanMesh.verticesBuffer().getBuffer());
                    vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    vkCmdBindIndexBuffer(cmdHandle, vulkanMesh.indicesBuffer().getBuffer(), 0, VK_INDEX_TYPE_UINT32);
                    vkCmdDrawIndexed(cmdHandle, vulkanMesh.numIndices(), 1, 0, 0, 0);
                }
            }

            vkCmdEndRendering(cmdHandle);

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }

    public void resize(VkCtx vkCtx) {
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(depthAttachmentsInfo).forEach(VkRenderingAttachmentInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        Arrays.asList(depthAttachments).forEach(a -> a.cleanup(vkCtx));
        depthAttachments = createDepthAttachments(vkCtx);
        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        depthAttachmentsInfo = createDepthAttachmentsInfo(vkCtx, depthAttachments, clrValueDepth);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo, depthAttachmentsInfo);
    }

    private void setPushConstants(VkCommandBuffer cmdHandle, Matrix4f projMatrix, Matrix4f modelMatrix) {
        projMatrix.get(pushConstBuff);
        modelMatrix.get(VkUtils.MAT4X4_SIZE, pushConstBuff);
        vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(), VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstBuff);
    }
}
