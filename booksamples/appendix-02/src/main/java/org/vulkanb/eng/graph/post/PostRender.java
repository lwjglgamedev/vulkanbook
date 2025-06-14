package org.vulkanb.eng.graph.post;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.*;

import java.nio.*;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;

public class PostRender {
    public static final int COLOR_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    private static final String DESC_ID_ATT = "POST_DESC_ID_ATT";
    private static final String DESC_ID_SCREEN_SIZE = "POST_DESC_ID_SCREEN_SIZE";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/post_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/post_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final DescSetLayout attDescSetLayout;
    private final VkClearValue clrValueColor;
    private final DescSetLayout frgUniformDescSetLayout;
    private final Pipeline pipeline;
    private final VkBuffer scrSizeBuff;
    private final SpecConstants specConstants;
    private final TextureSampler textureSampler;
    private Attachment colorAttachment;
    private VkRenderingAttachmentInfo.Buffer colorAttachmentInfo;
    private VkRenderingInfo renderInfo;

    public PostRender(VkCtx vkCtx, Attachment srcAttachment) {
        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));

        colorAttachment = createColorAttachment(vkCtx);
        colorAttachmentInfo = createColorAttachmentInfo(colorAttachment, clrValueColor);
        renderInfo = createRenderInfo(colorAttachment, colorAttachmentInfo);

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);

        var layoutInfo = new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        attDescSetLayout = new DescSetLayout(vkCtx, layoutInfo);
        createAttDescSet(vkCtx, attDescSetLayout, srcAttachment, textureSampler);

        layoutInfo = new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        frgUniformDescSetLayout = new DescSetLayout(vkCtx, layoutInfo);
        scrSizeBuff = VkUtils.createHostVisibleBuff(vkCtx, VkUtils.VEC2_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                DESC_ID_SCREEN_SIZE, frgUniformDescSetLayout);
        setScrSizeBuffer(vkCtx);

        specConstants = new SpecConstants();
        ShaderModule[] shaderModules = createShaderModules(vkCtx, specConstants);

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{attDescSetLayout, frgUniformDescSetLayout});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static void createAttDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, Attachment attachment,
                                         TextureSampler sampler) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ATT, 1, descSetLayout)[0];
        descSet.setImage(device, attachment.getImageView(), sampler, 0);
    }

    private static Attachment createColorAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                COLOR_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, 1);
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

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{COLOR_FORMAT})
                .setDescSetLayouts(descSetLayouts);
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

    private static ShaderModule[] createShaderModules(VkCtx vkCtx, SpecConstants specConstants) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV, specConstants.getSpecInfo()),
        };
    }

    public void cleanup(VkCtx vkCtx) {
        clrValueColor.free();
        colorAttachment.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        attDescSetLayout.cleanup(vkCtx);
        frgUniformDescSetLayout.cleanup(vkCtx);
        pipeline.cleanup(vkCtx);
        renderInfo.free();
        colorAttachmentInfo.free();
        scrSizeBuff.cleanup(vkCtx);
        specConstants.cleanup();
    }

    public Attachment getAttachment() {
        return colorAttachment;
    }

    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, Attachment srcAttachment) {
        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, srcAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            VkUtils.imageBarrier(stack, cmdHandle, colorAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            vkCmdBeginRendering(cmdHandle, renderInfo);

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

            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(2)
                    .put(0, descAllocator.getDescSet(DESC_ID_ATT).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_SCREEN_SIZE).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            vkCmdDraw(cmdHandle, 3, 1, 0, 0);

            vkCmdEndRendering(cmdHandle);
        }
    }

    public void resize(VkCtx vkCtx, Attachment srcAttachment) {
        renderInfo.free();
        colorAttachment.cleanup(vkCtx);
        colorAttachmentInfo.free();
        colorAttachment = createColorAttachment(vkCtx);
        colorAttachmentInfo = createColorAttachmentInfo(colorAttachment, clrValueColor);
        renderInfo = createRenderInfo(colorAttachment, colorAttachmentInfo);

        DescAllocator descAllocator = vkCtx.getDescAllocator();
        DescSet descSet = descAllocator.getDescSet(DESC_ID_ATT);
        descSet.setImage(vkCtx.getDevice(), srcAttachment.getImageView(), textureSampler, 0);

        setScrSizeBuffer(vkCtx);
    }

    private void setScrSizeBuffer(VkCtx vkCtx) {
        long mappedMemory = scrSizeBuff.map(vkCtx);
        FloatBuffer dataBuff = MemoryUtil.memFloatBuffer(mappedMemory, (int) scrSizeBuff.getRequestedSize());
        VkExtent2D swapChainExtent = vkCtx.getSwapChain().getSwapChainExtent();
        dataBuff.put(0, swapChainExtent.width());
        dataBuff.put(1, swapChainExtent.height());
        scrSizeBuff.unMap(vkCtx);
    }
}
