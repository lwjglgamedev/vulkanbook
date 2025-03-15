package org.vulkanb.eng.graph.light;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.scn.MrtAttachments;
import org.vulkanb.eng.graph.shadow.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.VK13.*;

public class LightRender {

    private static final int COLOR_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;
    private static final String DESC_ID_ATT = "LIGHT_DESC_ID_ATT";
    private static final String DESC_ID_LIGHTS = "LIGHT_DESC_ID_LIGHTS";
    private static final String DESC_ID_SCENE = "LIGHT_DESC_ID_SCENE ";
    private static final String DESC_ID_SHADOW_MATRICES = "LIGHT_DESC_ID_SHADOW_MATRICES";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/light_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/light_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";

    private final DescSetLayout attDescSetLayout;
    private final VkClearValue clrValueColor;
    private final LightSpecConsts lightSpecConsts;
    private final VkBuffer[] lightsBuffs;
    private final Pipeline pipeline;
    private final VkBuffer[] sceneBuffs;
    private final DescSetLayout sceneDescSetLayout;
    private final VkBuffer[] shadowMatrices;
    private final DescSetLayout storageDescSetLayout;
    private final TextureSampler textureSampler;
    private Attachment attColor;
    private VkRenderingAttachmentInfo.Buffer attInfoColor;
    private VkRenderingInfo renderInfo;


    public LightRender(VkCtx vkCtx, List<Attachment> attachments) {
        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));

        attColor = createColorAttachment(vkCtx);
        attInfoColor = createColorAttachmentInfo(attColor, clrValueColor);
        renderInfo = createRenderInfo(attColor, attInfoColor);

        lightSpecConsts = new LightSpecConsts();
        ShaderModule[] shaderModules = createShaderModules(vkCtx, lightSpecConsts);

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);
        int numAttachments = attachments.size();
        DescSetLayout.LayoutInfo[] descSetLayouts = new DescSetLayout.LayoutInfo[numAttachments + 1];
        for (int i = 0; i < numAttachments; i++) {
            descSetLayouts[i] = new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, i, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        descSetLayouts[numAttachments] = new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, numAttachments, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        attDescSetLayout = new DescSetLayout(vkCtx, descSetLayouts);
        createAttDescSet(vkCtx, attDescSetLayout, attachments, textureSampler);

        storageDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 0, 1,
                VK_SHADER_STAGE_FRAGMENT_BIT));
        long buffSize = (long) (VkUtils.VEC4_SIZE + VkUtils.VEC3_SIZE) * Scene.MAX_LIGHTS;
        lightsBuffs = VkUtils.createHostVisibleBuffs(vkCtx, buffSize, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, DESC_ID_LIGHTS, storageDescSetLayout);

        sceneDescSetLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1,
                VK_SHADER_STAGE_FRAGMENT_BIT));
        buffSize = VkUtils.VEC3_SIZE * 2 + VkUtils.INT_SIZE + VkUtils.MAT4X4_SIZE;
        sceneBuffs = VkUtils.createHostVisibleBuffs(vkCtx, buffSize, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, DESC_ID_SCENE, sceneDescSetLayout);

        buffSize = (VkUtils.MAT4X4_SIZE + VkUtils.VEC4_SIZE) * Scene.SHADOW_MAP_CASCADE_COUNT;
        shadowMatrices = VkUtils.createHostVisibleBuffs(vkCtx, buffSize, VkUtils.MAX_IN_FLIGHT,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, DESC_ID_SHADOW_MATRICES, storageDescSetLayout);

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{attDescSetLayout, storageDescSetLayout,
                storageDescSetLayout, sceneDescSetLayout});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static void createAttDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, List<Attachment> attachments,
                                         TextureSampler sampler) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ATT, 1, descSetLayout)[0];
        List<ImageView> imageViews = new ArrayList<>();
        attachments.forEach(a -> imageViews.add(a.getImageView()));
        descSet.setImages(device, imageViews, sampler, 0);
    }

    private static Attachment createColorAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                COLOR_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, 1);
    }

    private static VkRenderingAttachmentInfo.Buffer createColorAttachmentInfo(Attachment attachment, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc(1)
                .sType$Default()
                .imageView(attachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), new int[]{COLOR_FORMAT})
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo createRenderInfo(Attachment colorAttachment, VkRenderingAttachmentInfo.Buffer colorAttachmentInfo) {
        VkRenderingInfo result;
        try (var stack = MemoryStack.stackPush()) {
            VkExtent2D extent = VkExtent2D.calloc(stack);
            extent.width(colorAttachment.getImage().getWidth());
            extent.height(colorAttachment.getImage().getHeight());
            var renderArea = VkRect2D.calloc(stack).extent(extent);

            result = VkRenderingInfo.calloc()
                    .sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1)
                    .pColorAttachments(colorAttachmentInfo);
        }
        return result;
    }

    private static ShaderModule[] createShaderModules(VkCtx vkCtx, LightSpecConsts lightSpecConsts) {
        if (EngCfg.getInstance().isShaderRecompilation()) {
            ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_vertex_shader);
            ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER_FILE_GLSL, Shaderc.shaderc_glsl_fragment_shader);
        }
        return new ShaderModule[]{
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV, lightSpecConsts.getSpecInfo()),
        };
    }

    public void cleanup(VkCtx vkCtx) {
        storageDescSetLayout.cleanup(vkCtx);
        Arrays.asList(lightsBuffs).forEach(b -> b.cleanup(vkCtx));
        sceneDescSetLayout.cleanup(vkCtx);
        Arrays.asList(sceneBuffs).forEach(b -> b.cleanup(vkCtx));
        pipeline.cleanup(vkCtx);
        attDescSetLayout.cleanup(vkCtx);
        textureSampler.cleanup(vkCtx);
        lightSpecConsts.cleanup();
        Arrays.asList(shadowMatrices).forEach(b -> b.cleanup(vkCtx));
        renderInfo.free();
        attColor.cleanup(vkCtx);
        attInfoColor.free();
        clrValueColor.free();
    }

    public Attachment getAttachment() {
        return attColor;
    }

    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, MrtAttachments mrtAttachments,
                       Attachment depthAttachment, CascadeShadows cascadeShadows, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            Scene scene = engCtx.scene();

            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, attColor.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            List<Attachment> attachments = mrtAttachments.getColorAttachments();
            int numAttachments = attachments.size();
            for (int i = 0; i < numAttachments; i++) {
                Attachment attachment = attachments.get(i);
                VkUtils.imageBarrier(stack, cmdHandle, attachment.getImage().getVkImage(),
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
            }

            VkUtils.imageBarrier(stack, cmdHandle, depthAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

            updateCascadeShadowMatrices(vkCtx, cascadeShadows, currentFrame);

            vkCmdBeginRendering(cmdHandle, renderInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            Image colorImage = attColor.getImage();
            int width = colorImage.getWidth();
            int height = colorImage.getHeight();
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
            LongBuffer descriptorSets = stack.mallocLong(4)
                    .put(0, descAllocator.getDescSet(DESC_ID_ATT).getVkDescriptorSet())
                    .put(1, descAllocator.getDescSet(DESC_ID_LIGHTS, currentFrame).getVkDescriptorSet())
                    .put(2, descAllocator.getDescSet(DESC_ID_SHADOW_MATRICES, currentFrame).getVkDescriptorSet())
                    .put(3, descAllocator.getDescSet(DESC_ID_SCENE, currentFrame).getVkDescriptorSet());
            updateSceneInfo(vkCtx, scene, currentFrame);
            updateLights(vkCtx, scene, currentFrame);

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            vkCmdDraw(cmdHandle, 3, 1, 0, 0);

            vkCmdEndRendering(cmdHandle);
        }
    }

    public void resize(VkCtx vkCtx, List<Attachment> attachments) {
        renderInfo.free();
        attInfoColor.free();
        attColor.cleanup(vkCtx);

        attColor = createColorAttachment(vkCtx);
        attInfoColor = createColorAttachmentInfo(attColor, clrValueColor);
        renderInfo = createRenderInfo(attColor, attInfoColor);

        DescAllocator descAllocator = vkCtx.getDescAllocator();
        DescSet descSet = descAllocator.getDescSet(DESC_ID_ATT);
        List<ImageView> imageViews = new ArrayList<>();
        attachments.forEach(a -> imageViews.add(a.getImageView()));
        descSet.setImages(vkCtx.getDevice(), imageViews, textureSampler, 0);
    }

    public void transitionToPresent(VkCtx vkCtx, CmdBuffer cmdBuffer, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            long swapChainImage = vkCtx.getSwapChain().getImageView(imageIndex).getVkImage();
            VkUtils.imageBarrier(stack, cmdBuffer.getVkCommandBuffer(), swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }

    private void updateCascadeShadowMatrices(VkCtx vkCtx, CascadeShadows cascadeShadows, int currentFrame) {
        VkBuffer buff = shadowMatrices[currentFrame];
        long mappedMemory = buff.map(vkCtx);
        ByteBuffer dataBuff = MemoryUtil.memByteBuffer(mappedMemory, (int) buff.getRequestedSize());
        int offset = 0;
        List<CascadeData> cascadeDataList = cascadeShadows.getCascadeData();
        int numCascades = cascadeDataList.size();
        for (int i = 0; i < numCascades; i++) {
            CascadeData cascadeData = cascadeDataList.get(i);
            cascadeData.getProjViewMatrix().get(offset, dataBuff);
            dataBuff.putFloat(offset + VkUtils.MAT4X4_SIZE, cascadeData.getSplitDistance());
            offset += VkUtils.MAT4X4_SIZE + VkUtils.VEC4_SIZE;
        }
        buff.unMap(vkCtx);
    }

    private void updateLights(VkCtx vkCtx, Scene scene, int currentFrame) {
        Light[] lights = scene.getLights();
        VkBuffer buff = lightsBuffs[currentFrame];
        long mappedMemory = buff.map(vkCtx);
        ByteBuffer dataBuff = MemoryUtil.memByteBuffer(mappedMemory, (int) buff.getRequestedSize());

        int offset = 0;
        int numLights = lights != null ? lights.length : 0;
        for (int i = 0; i < numLights; i++) {
            Light light = lights[i];
            light.getPosition().get(offset, dataBuff);
            offset += VkUtils.VEC4_SIZE;
            light.getColor().get(offset, dataBuff);
            offset += VkUtils.VEC3_SIZE;
        }

        buff.unMap(vkCtx);
    }

    private void updateSceneInfo(VkCtx vkCtx, Scene scene, int currentFrame) {
        VkBuffer buff = sceneBuffs[currentFrame];
        long mappedMemory = buff.map(vkCtx);
        ByteBuffer dataBuff = MemoryUtil.memByteBuffer(mappedMemory, (int) buff.getRequestedSize());

        int offset = 0;
        scene.getCamera().getPosition().get(offset, dataBuff);
        offset += VkUtils.VEC3_SIZE;

        scene.getAmbientLight().get(offset, dataBuff);
        offset += VkUtils.VEC3_SIZE;

        Light[] lights = scene.getLights();
        int numLights = lights != null ? lights.length : 0;
        dataBuff.putInt(offset, numLights);
        offset += VkUtils.INT_SIZE;

        scene.getCamera().getViewMatrix().get(offset, dataBuff);

        buff.unMap(vkCtx);
    }
}
