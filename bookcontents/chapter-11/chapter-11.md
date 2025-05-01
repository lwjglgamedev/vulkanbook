# Chapter 11 - Post processing

In this chapter we will implement a post-processing stage. We will render to a buffer instead of directly rendering to a swp chain image and once, we have finished we
will apply some effects suc us FXAA filtering and gamma correction.

You can find the complete source code for this chapter [here](../../booksamples/chapter-11).

## Specialization constants

We will first introduce a new concept, specialization constants, which are a way to update constants in shaders at module loading time. That is, we can
modify the value of a constant without the need to recompile the shader. We will use this concept in some of the shaders in this chapter. This an example
of a specialization constant defined in GLSL

```glsl
layout (constant_id = 0) const int SAMPLE_CONSTANT = 33;
```

We can modify the value above when creating the pipeline, without recompiling the shader. If we do not set the values for the specialization constants we will
just use the value assigned in the shader.

Specialization constants, for a shader, are defined by using the `VkSpecializationInfo` structure which basically defines the following fields:
- `pData`:  a Pointer to a buffer which will hold the data for the specialization constants.
- `pMapEntries`: A map of entries, having one entry per specialization constant.

Each entry is modelled by the `VkSpecializationMapEntry` struct which has the following fields:
- `constantID`: The identifier of the constant in the SPIR-V file (The number associated to the `constant_id` field).
- `offset`: The byte offset of the specialization constant value within the supplied data buffer.
- `size`: The size in bytes of the constant.

We will modify the `ShaderModule` class to be able to receive a `VkSpecializationInfo` instance in the constructor:

```java
public class ShaderModule {
    ...
    private final VkSpecializationInfo specInfo;

    public ShaderModule(VkCtx vkCtx, int shaderStage, String shaderSpvFile, VkSpecializationInfo specInfo) {
        ...
            this.specInfo = specInfo;
        ...
    }
    ...
    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }    
}
```

We need to modify the `Pipeline` class to use the `VkSpecializationInfo` information when creating the shader stages:

```java
public class Pipeline {
    ...
    public Pipeline(VkCtx vkCtx, PipelineBuildInfo buildInfo) {
        ...
            ShaderModule[] shaderModules = buildInfo.getShaderModules();
            int numModules = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModule.getShaderStage())
                        .module(shaderModule.getHandle())
                        .pName(main);
                if (shaderModule.getSpecInfo() != null) {
                    shaderStages.get(i).pSpecializationInfo(shaderModule.getSpecInfo());
                }
            }
        ...
    }
    ...
}
```

## Rendering to an attachment

We will start first by modifying the `ScnRender` class to use its own attachment for color output instead of using swap chain images. If you recall we already did have
attachments for depth information. The changes in the `ScnRender` class start like this:

```java
public class ScnRender {

    private static final int COLOR_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    ...
    private Attachment attColor;
    private Attachment attDepth;
    private VkRenderingAttachmentInfo.Buffer attInfoColor;
    private VkRenderingAttachmentInfo attInfoDepth;
    private VkRenderingInfo renderInfo;

    public ScnRender(VkCtx vkCtx, EngCtx engCtx) {
        clrValueColor = VkClearValue.calloc().color(
                c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));
        clrValueDepth = VkClearValue.calloc().color(c -> c.float32(0, 1.0f));
        attColor = createColorAttachment(vkCtx);
        attDepth = createDepthAttachment(vkCtx);
        attInfoColor = createColorAttachmentInfo(attColor, clrValueColor);
        attInfoDepth = createDepthAttachmentInfo(attDepth, clrValueDepth);
        renderInfo = createRenderInfo(attColor, attInfoColor, attInfoDepth);
        ...
    }
    ...
}
```

We will have now two attachments, one for the the color data and one for the depth data. We will not have separate instances per swap chain images
to avoid waisting too much memory, we will manage synchronization differently. We now need to create an Attachment (image and image view) for outputting color,
therefore we need a `createColorAttachment` method:

```java
public class ScnRender {
    ...
    private static Attachment createColorAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                COLOR_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
    }
    ...
}
```

The method just creates an attachment which dimensions are equal to the swap chain images. The `createColorAttachmentInfo` method needs to be updated:

```java
public class ScnRender {
    ...
    private static VkRenderingAttachmentInfo.Buffer createColorAttachmentInfo(Attachment attachment, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc(1)
                .sType$Default()
                .imageView(attachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }
    ...
}
```

In addition to just having a single `VkRenderingAttachmentInfo.Buffer`, the `imageLayout` is now `VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL` instead of being
`VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR` since the image is not related yo the swp chain now. The methods `createDepthAttachment` and `createDepthAttachmentInfo` are
like this:

```java
public class ScnRender {
    ...
    private static Attachment createDepthAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                DEPTH_FORMAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
    }
    ...
    private static VkRenderingAttachmentInfo createDepthAttachmentInfo(Attachment depthAttachment, VkClearValue clearValue) {
        return VkRenderingAttachmentInfo.calloc()
                .sType$Default()
                .imageView(depthAttachment.getImageView().getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .clearValue(clearValue);
    }
    ...
}
```

The `createRenderInfo` method needs also to be simplified since we do not have arrays of rendering information:

```java
public class ScnRender {
    ...
    private static VkRenderingInfo createRenderInfo(Attachment colorAttachment, VkRenderingAttachmentInfo.Buffer colorAttachmentInfo,
                                                    VkRenderingAttachmentInfo depthAttachmentInfo) {
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
                    .pColorAttachments(colorAttachmentInfo)
                    .pDepthAttachment(depthAttachmentInfo);
        }
        return result;
    }
    ...
}
```

The `createPipeline` method needs to be updated to set the color format to the one used by `ScnRender` class, instead of using the one used by in the swap chain:

```java
public class ScnRender {
    ...
    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new VtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), COLOR_FORMAT)
                .setDepthFormat(DEPTH_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.MAT4X4_SIZE),
                                new PushConstRange(VK_SHADER_STAGE_FRAGMENT_BIT, VkUtils.MAT4X4_SIZE, VkUtils.INT_SIZE),
                        })
                .setDescSetLayouts(descSetLayouts)
                .setUseBlend(true);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }
    ...
}
```

The `createShaderModules` method needs to be updated due to the specialization chanages:

```java
public class ScnRender {
    ...
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
    ...
}
```

We need to update the `cleanup` method and add a *getter* for the color attachment (we will use it in new render classes)

```java
public class ScnRender {
    ...
    public void cleanup(VkCtx vkCtx) {
        ...
        renderInfo.free();
        attInfoDepth.free();
        attInfoColor.free();
        attColor.cleanup(vkCtx);
        attDepth.cleanup(vkCtx);
        ...
    }

    public Attachment getAttColor() {
        return attColor;
    }
    ...
}
```

We need also to update the `render` method to use the color attachment that we have created:

```java
public class ScnRender {
    ...
    public void render(EngCtx engCtx, VkCtx vkCtx, CmdBuffer cmdBuffer, ModelsCache modelsCache,
                       MaterialsCache materialsCache, int currentFrame) {
        try (var stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, attColor.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);
            VkUtils.imageBarrier(stack, cmdHandle, attDepth.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_DEPTH_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_PIPELINE_STAGE_2_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_2_LATE_FRAGMENT_TESTS_BIT,
                    VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_DEPTH_BIT);

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

            VkUtils.copyMatrixToBuffer(vkCtx, buffViewMatrices[currentFrame], engCtx.scene().getCamera().getViewMatrix(), 0);
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
        }
    }
    ...
}
```

You can see that we no longer need the `imageIndex` attribute and no need to index render information by it. There is, however, one relevant change.
We no longer need the final barrier to set the layout to presentation model. We will not use the color attachment to present it. Instead,
we will use it to post-process it and finally, copy it to the swap chain image.

Finally, the `resize` method needs also to be updated due to the change in the attachments:

```java
public class ScnRender {
    ...
    public void resize(EngCtx engCtx, VkCtx vkCtx) {
        renderInfo.free();
        attInfoColor.free();
        attInfoDepth.free();
        attColor.cleanup(vkCtx);
        attDepth.cleanup(vkCtx);

        attColor = createColorAttachment(vkCtx);
        attDepth = createDepthAttachment(vkCtx);
        attInfoColor = createColorAttachmentInfo(attColor, clrValueColor);
        attInfoDepth = createDepthAttachmentInfo(attDepth, clrValueDepth);
        renderInfo = createRenderInfo(attColor, attInfoColor, attInfoDepth);

        VkUtils.copyMatrixToBuffer(vkCtx, buffProjMatrix, engCtx.scene().getProjection().getProjectionMatrix(), 0);
    }
    ...
}
```

## Post processing

We will use a post processing stage to filter the rendered results and to apply tone correction. We will perform this by rendering a quad to a string using
the attachment used for rendering in the `ScnRender` class as a texture to render to another image attachment applying the filtering and tone correction actions.
This will be done in a new class named `PostRender`. The class starts like this:

```java
package org.vulkanb.eng.graph.post;

import org.lwjgl.system.*;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.*;

import java.nio.*;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.*;
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

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{attDescSetLayout, frgUniformDescSetLayout });
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }
    ...
}
```

As you can see is quite similar to the other render class. In this case we just create a color attachment (no need for depth attachment). We wll need a texture
sampler to access the output attachment coming from the `ScnRender` class, which is received as a parameter in the `srcAttachment` variable. We will need a descriptor
set to access that texture and another one to store screen dimensions. We have added a new constant in the `VkUtils` class to model the size of a `vec2`.


```java
public class VkUtils {
    ...
    public static final int VEC2_SIZE = 2 * FLOAT_SIZE;
    ...
}
```

Back to the `PostRender` class, the methods associated to the creation of the output attachment, pipeline and render information are similar to the ones used in the
`ScnRender` class:

```java
public class PostRender {
    ...
    private static Attachment createColorAttachment(VkCtx vkCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D swapChainExtent = swapChain.getSwapChainExtent();
        return new Attachment(vkCtx, swapChainExtent.width(), swapChainExtent.height(),
                COLOR_FORMAT, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
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
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(), COLOR_FORMAT)
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
    ...
}
```

You may have noticed that, when creating the pipeline we are using a new class named `EmptyVtxBuffStruct`. This class is defined like this:

```java
package org.vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

public class EmptyVtxBuffStruct {

    private final VkPipelineVertexInputStateCreateInfo vi;

    public EmptyVtxBuffStruct() {
        vi = VkPipelineVertexInputStateCreateInfo.calloc();
        vi.sType$Default()
                .pVertexBindingDescriptions(null)
                .pVertexAttributeDescriptions(null);
    }

    public void cleanup() {
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
```

We just define an empty structure. Remember that we will just be drawing a quad, and in order to do so, we do not even need to bind to some vertices or indices
buffer. We will see how this is done in the shader.

As mentioned before, we will need a descriptor set to use a uniform which will store screen size. The `createAttDescSet` method is where this is done:

```java
public class PostRender {
    ...
    private static void createAttDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, Attachment attachment,
                                         TextureSampler sampler) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ATT, 1, descSetLayout)[0];
        descSet.setImage(device, attachment.getImageView(), sampler, 0);
    }
    ...
}
```

We will use a specialization constant to control if we apply FXAA filtering or not. You may have noticed that we defined a `SpecConstants` class to set this up:

```java
package org.vulkanb.eng.graph.post;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.VkUtils;

import java.nio.ByteBuffer;

public class SpecConstants {

    private final ByteBuffer data;
    private final VkSpecializationMapEntry.Buffer specEntryMap;
    private final VkSpecializationInfo specInfo;

    public SpecConstants() {
        var engCfg = EngCfg.getInstance();
        data = MemoryUtil.memAlloc(VkUtils.INT_SIZE);
        data.putInt(engCfg.isFxaa() ? 1 : 0);
        data.flip();

        specEntryMap = VkSpecializationMapEntry.calloc(1);
        specEntryMap.get(0)
                .constantID(0)
                .size(VkUtils.INT_SIZE)
                .offset(0);

        specInfo = VkSpecializationInfo.calloc();
        specInfo.pData(data)
                .pMapEntries(specEntryMap);
    }

    public void cleanup() {
        MemoryUtil.memFree(specEntryMap);
        specInfo.free();
        MemoryUtil.memFree(data);
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
```

We just crate one entry, which uses a buffer to host one integer, which will model a flag to use FXAA or not. We just get the value of the flag from a new configuration
parameter of the `EngCfg` class. Please notice that we need to keep track of the buffer and the `VkSpecializationInfo` data until we have created the pipeline which uses the shader modules associated to the specialization constants. The new configuration parameter is defined like this:

```java
public class EngCfg {
    ...
    private boolean fxaa;
    ...
    private EngCfg() {
        ...
            fxaa = Boolean.parseBoolean(props.getOrDefault("fxaa", true).toString());
        ...
    }
    ...
    public boolean isFxaa() {
        return fxaa;
    }
}
```

Back to the `PostRender` class, we need to pass the instance to `SpecConstants` in the `ShaderModule` constructor of the fragment shader.

```java
public class PostRender {
    ...
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
    ...
}
```

We need to add a `cleanup` method and a *getter* to get access to the output attachment:

```java
public class PostRender {
    ...
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
    ...
}
```

The `render` method is defined like this:

```java
public class PostRender {
    ...
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
    ...
}
```

First, we transition the output attachment used by the `ScnRender` class to `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`, since we will be using this attachment
as a source, we will not be modifying it. We need this to happen when we reach `VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT` stage and to access in read only mode
(`VK_ACCESS_2_SHADER_READ_BIT`). This is achieved with the first barrier. We need also to ensure that the output attachment of this class is in
`VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL` layout by setting the second barrier. AFter that, we just bind the pipeline, set the view port and scissors, bind
the descriptor sets and perform a call to `vkCmdDraw`. Wih this call we just draw 3 vertices without needing to bind any vertex or index buffer. Remember
that we will just need to render a quad in clip space, so we do not need even the coordinates, we will generate them using the vertex fragment. We will not 
even using two triangles to render a quad, one single triangle is enough for us to achieve the same effect. We will see how it is done in the  vertex shader.

We to define the method to set the screen size dimensions to the associated buffera and to create a `resize` method:

```java
public class PostRender {
    ...
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
```

Now it is turn for the vertex shader `post_vtx.glsl`:

```glsl
#version 450

layout (location = 0) out vec2 outTextCoord;

void main()
{
    outTextCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(outTextCoord.x * 2.0f - 1.0f, outTextCoord.y * -2.0f + 1.0f, 0.0f, 1.0f);
}
```

So let's dissect what `outTextCoord` will be depending on the value of `gl_VertexIndex`:

- For the first vertex, `gl_VertexIndex` will have the vale `0`, shifting one position to the left will just be also `0` and performing an AND operation with `2` (`0b10`)
will just be also `0` for the `x` coordinate of `outTextCoord`. The `y` coordinate will be also `0`. So we will have (`0`, `0`).
- For the second vertex, `gl_VertexIndex` will have the vale `1` (`0b01`), shifting one position will be `1` (`0b10`) and performing an AND operation with `2` (`0b10`) will
be `2` (`0b10`) for the `x` coordinate of `outTextCoord`. The `y`coordinate will be `0`. So we will have (`2`, `0`).
- For the second vertex, `gl_VertexIndex` will have the vale `2` (`0b10`), shifting one position will be `0` (`0b00`) and performing an AND operation with `2` (`0b10`) will
be `2` (`0b00`) for the `x` coordinate of `outTextCoord`. The `y`coordinate will be `2`. So we will have (`0`, `2`).

Now, let's review what will be the value of `gl_Position` will be depending on the value of `outTextCoord`:
- For the first vertex, we will have (`0`, `0`) for `outTextCoord`, so `gl_Position` will be (`-1`, `1`, `0`, `1`).
- For the second vertex, we will have (`2`, `0`) for `outTextCoord`, so `gl_Position` will be (`3`, `1`, `0`, `1`).
- For the third vertex, we will have (`0`, `2`) for `outTextCoord`, so `gl_Position` will be (`-1`, `-3`, `0`, `1`).

The next figure shows the resulting triangle with texture coordinates in red and position in green and with dashed line the quad that is withing clip space coordinates
([-1,1], [1, -1]). As you can see by drawing a triangle we get a quad within clips space that we will use to generate the post-processing image.

[quad](./rc11-quad.svg)

The fragment shader is defined like this:

```java
#version 450

layout (constant_id = 0) const int USE_FXAA = 0;

const float GAMMA_CONST = 0.4545;
const float SPAN_MAX = 8.0;
const float REDUCE_MIN = 1.0/128.0;
const float REDUCE_MUL = 1.0/32.0;

layout (location = 0) in vec2 inTextCoord;
layout (location = 0) out vec4 outFragColor;

layout (set = 0, binding = 0) uniform sampler2D inputTexture;
layout (set = 1, binding = 0) uniform ScreenSize {
    vec2 size;
} screenSize;

vec4 gamma(vec4 color) {
    return color = vec4(pow(color.rgb, vec3(GAMMA_CONST)), color.a);
}

// Credit: https://mini.gmshaders.com/p/gm-shaders-mini-fxaa
vec4 fxaa(sampler2D tex, vec2 uv) {
    vec2 u_texel = 1.0 / screenSize.size;

	//Sample center and 4 corners
    vec3 rgbCC = texture(tex, uv).rgb;
    vec3 rgb00 = texture(tex, uv+vec2(-0.5,-0.5)*u_texel).rgb;
    vec3 rgb10 = texture(tex, uv+vec2(+0.5,-0.5)*u_texel).rgb;
    vec3 rgb01 = texture(tex, uv+vec2(-0.5,+0.5)*u_texel).rgb;
    vec3 rgb11 = texture(tex, uv+vec2(+0.5,+0.5)*u_texel).rgb;

	//Luma coefficients
    const vec3 luma = vec3(0.299, 0.587, 0.114);
	//Get luma from the 5 samples
    float lumaCC = dot(rgbCC, luma);
    float luma00 = dot(rgb00, luma);
    float luma10 = dot(rgb10, luma);
    float luma01 = dot(rgb01, luma);
    float luma11 = dot(rgb11, luma);

	//Compute gradient from luma values
    vec2 dir = vec2((luma01 + luma11) - (luma00 + luma10), (luma00 + luma01) - (luma10 + luma11));
	//Diminish dir length based on total luma
    float dirReduce = max((luma00 + luma10 + luma01 + luma11) * REDUCE_MUL, REDUCE_MIN);
	//Divide dir by the distance to nearest edge plus dirReduce
    float rcpDir = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
	//Multiply by reciprocal and limit to pixel span
    dir = clamp(dir * rcpDir, -SPAN_MAX, SPAN_MAX) * u_texel.xy;

	//Average middle texels along dir line
    vec4 A = 0.5 * (
        texture(tex, uv - dir * (1.0/6.0)) +
        texture(tex, uv + dir * (1.0/6.0)));

	//Average with outer texels along dir line
    vec4 B = A * 0.5 + 0.25 * (
        texture(tex, uv - dir * (0.5)) +
        texture(tex, uv + dir * (0.5)));


	//Get lowest and highest luma values
    float lumaMin = min(lumaCC, min(min(luma00, luma10), min(luma01, luma11)));
    float lumaMax = max(lumaCC, max(max(luma00, luma10), max(luma01, luma11)));

	//Get average luma
	float lumaB = dot(B.rgb, luma);
	//If the average is outside the luma range, using the middle average
    return ((lumaB < lumaMin) || (lumaB > lumaMax)) ? A : B;
}

void main() {
    if (USE_FXAA == 0) {
        outFragColor = gamma(texture(inputTexture, inTextCoord));
        return;
    }

    outFragColor = fxaa(inputTexture, inTextCoord);
    outFragColor = gamma(outFragColor);
}
```

We use the specialization constant flag that enables / disables FXAA filtering. As you can see we `inputTexture` descriptor set is the result of the scene rendering
stage. FXAA implementation has been obtained from [here](https://mini.gmshaders.com/p/gm-shaders-mini-fxaa).

## Copying to the swap chain

In order to copy the processed image to the swap chain, we have two possibilities:
- We can just use the post processing image as an input attachment and render a quad to copy each fragment.
- We an copy the image using `vkCmdBlitImage` and `vkCmdCopyImage` to copy the post processing image contents.

We will chose the first option, since it seems to be equally performant and does not impose any restriction sin terms in difference on sampling between images.

The process will be similar to post processing stage but much more simpler. We will just create a class named `SwapChainRender` which is defined like this:

```java
package org.vulkanb.eng.graph.swap;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.graph.vk.*;

import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_IMAGE_LAYOUT_ATTACHMENT_OPTIMAL_KHR;
import static org.lwjgl.vulkan.VK13.*;

public class SwapChainRender {

    private static final String DESC_ID_ATT = "FWD_DESC_ID_ATT";
    private static final String FRAGMENT_SHADER_FILE_GLSL = "resources/shaders/swap_frg.glsl";
    private static final String FRAGMENT_SHADER_FILE_SPV = FRAGMENT_SHADER_FILE_GLSL + ".spv";
    private static final String VERTEX_SHADER_FILE_GLSL = "resources/shaders/swap_vtx.glsl";
    private static final String VERTEX_SHADER_FILE_SPV = VERTEX_SHADER_FILE_GLSL + ".spv";
    private final DescSetLayout attDescSetLayout;
    private final VkClearValue clrValueColor;
    private final Pipeline pipeline;
    private final TextureSampler textureSampler;
    private VkRenderingAttachmentInfo.Buffer[] colorAttachmentsInfo;
    private VkRenderingInfo[] renderInfo;

    public SwapChainRender(VkCtx vkCtx, Attachment srcAttachment) {
        clrValueColor = VkClearValue.calloc();
        clrValueColor.color(c -> c.float32(0, 0.0f).float32(1, 0.0f).float32(2, 0.0f).float32(3, 0.0f));

        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo);

        var textureSamplerInfo = new TextureSamplerInfo(VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VK_BORDER_COLOR_INT_OPAQUE_BLACK, 1, true);
        textureSampler = new TextureSampler(vkCtx, textureSamplerInfo);

        var layoutInfo = new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                0, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        attDescSetLayout = new DescSetLayout(vkCtx, layoutInfo);
        createAttDescSet(vkCtx, attDescSetLayout, srcAttachment, textureSampler);

        ShaderModule[] shaderModules = createShaderModules(vkCtx);

        pipeline = createPipeline(vkCtx, shaderModules, new DescSetLayout[]{attDescSetLayout});
        Arrays.asList(shaderModules).forEach(s -> s.cleanup(vkCtx));
    }

    private static void createAttDescSet(VkCtx vkCtx, DescSetLayout descSetLayout, Attachment attachment,
                                         TextureSampler sampler) {
        DescAllocator descAllocator = vkCtx.getDescAllocator();
        Device device = vkCtx.getDevice();
        DescSet descSet = descAllocator.addDescSets(device, DESC_ID_ATT, 1, descSetLayout)[0];
        descSet.setImage(device, attachment.getImageView(), sampler, 0);
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

    private static Pipeline createPipeline(VkCtx vkCtx, ShaderModule[] shaderModules, DescSetLayout[] descSetLayouts) {
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        var buildInfo = new PipelineBuildInfo(shaderModules, vtxBuffStruct.getVi(),
                vkCtx.getSurface().getSurfaceFormat().imageFormat())
                .setDescSetLayouts(descSetLayouts);
        var pipeline = new Pipeline(vkCtx, buildInfo);
        vtxBuffStruct.cleanup();
        return pipeline;
    }

    private static VkRenderingInfo[] createRenderInfo(VkCtx vkCtx, VkRenderingAttachmentInfo.Buffer[] colorAttachments) {
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
                        .pColorAttachments(colorAttachments[i]);
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
                new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, VERTEX_SHADER_FILE_SPV, null),
                new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, FRAGMENT_SHADER_FILE_SPV, null),
        };
    }

    public void cleanup(VkCtx vkCtx) {
        textureSampler.cleanup(vkCtx);
        attDescSetLayout.cleanup(vkCtx);
        pipeline.cleanup(vkCtx);
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        clrValueColor.free();
    }

    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, Attachment srcAttachment, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();

            long swapChainImage = swapChain.getImageView(imageIndex).getVkImage();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            VkUtils.imageBarrier(stack, cmdHandle, srcAttachment.getImage().getVkImage(),
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

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

            DescAllocator descAllocator = vkCtx.getDescAllocator();
            LongBuffer descriptorSets = stack.mallocLong(1)
                    .put(0, descAllocator.getDescSet(DESC_ID_ATT).getVkDescriptorSet());
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

            vkCmdDraw(cmdHandle, 3, 1, 0, 0);

            vkCmdEndRendering(cmdHandle);

            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_PIPELINE_STAGE_2_NONE,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }

    public void resize(VkCtx vkCtx, Attachment srcAttachment) {
        Arrays.asList(renderInfo).forEach(VkRenderingInfo::free);
        Arrays.asList(colorAttachmentsInfo).forEach(VkRenderingAttachmentInfo.Buffer::free);
        colorAttachmentsInfo = createColorAttachmentsInfo(vkCtx, clrValueColor);
        renderInfo = createRenderInfo(vkCtx, colorAttachmentsInfo);

        DescAllocator descAllocator = vkCtx.getDescAllocator();
        DescSet descSet = descAllocator.getDescSet(DESC_ID_ATT);
        descSet.setImage(vkCtx.getDevice(), srcAttachment.getImageView(), textureSampler, 0);
    }
}
```

In this case, we need to use the memory barriers to first set the swap chain image in `VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL` layout and after the render is finished in
the `VK_IMAGE_LAYOUT_PRESENT_SRC_KHR` layout to be ready to be presented.

The vertex shader (`swap_vtx.glsl`) is identical to the one used in the post-processing stage:

```glsl
#version 450

layout(location = 0) out vec2 outTextCoord;

void main()
{
    outTextCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
    gl_Position = vec4(outTextCoord.x * 2.0f - 1.0f, outTextCoord.y * -2.0f + 1.0f, 0.0f, 1.0f);
}
```

The fragment shader (`swap_frg.glsl`) is even simpler, we just sample the input attachment and dump it as it is:

```glsl
#version 450
layout (location = 0) in vec2 inTextCoord;

layout (location = 0) out vec4 outFragColor;

layout (set = 0, binding = 0) uniform sampler2D albedoSampler;

void main() {
    vec3 albedo = texture(albedoSampler, inTextCoord).rgb;
    outFragColor = vec4(albedo, 1.0);
}
```

## Final changes

In the `Render` class we need to use the new render classes:

```java
public class Render {
    ...
    private final PostRender postRender;
    ...
    private final SwapChainRender swapChainRender;
    ...
    public Render(EngCtx engCtx) {
        ...
        postRender = new PostRender(vkCtx, scnRender.getAttColor());
        swapChainRender = new SwapChainRender(vkCtx, postRender.getAttachment());
        ...
    }

    public void cleanup() {
        ...
        postRender.cleanup(vkCtx);
        swapChainRender.cleanup(vkCtx);
        ...
    }
    ...
    public void render(EngCtx engCtx) {
        SwapChain swapChain = vkCtx.getSwapChain();

        waitForFence(currentFrame);

        var cmdPool = cmdPools[currentFrame];
        var cmdBuffer = cmdBuffers[currentFrame];

        recordingStart(cmdPool, cmdBuffer);

        scnRender.render(engCtx, vkCtx, cmdBuffer, modelsCache, materialsCache, currentFrame);
        postRender.render(vkCtx, cmdBuffer, scnRender.getAttColor());

        int imageIndex;
        if (resize || (imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), imageAqSemphs[currentFrame])) < 0) {
            resize(engCtx);
            return;
        }

        swapChainRender.render(vkCtx, cmdBuffer, postRender.getAttachment(), imageIndex);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentFrame);

        resize = swapChain.presentImage(presentQueue, renderCompleteSemphs[currentFrame], imageIndex);

        currentFrame = (currentFrame + 1) % VkUtils.MAX_IN_FLIGHT;
    }
    
    private void resize(EngCtx engCtx) {
        ...
        postRender.resize(vkCtx, scnRender.getAttColor());
        swapChainRender.resize(vkCtx, postRender.getAttachment());
    }   
}
```

As you can see, since we are not rendering now to swap chain images, we can record the render commands for the `ScnRender` and `PostRender` classes prior to 
acquiring current swap chain image.

There is also a key important change that we need to perform. When setting the surface format, previously we tended to use `VK_FORMAT_B8G8R8A8_SRGB` which preformed
automatic gamma correction automatically. Now, we will be doing gamma correction manually in the post-processing stage (this will prevent to have issues when
using other stages, such as GUI drawing, that apply also gamma correction). Therefore, we need to change that format to this one: `VK_FORMAT_B8G8R8A8_UNORM`:

```java
public class Surface {
    ...
    private static SurfaceFormat calcSurfaceFormat(PhysDevice physDevice, long vkSurface) {
        ...
            imageFormat = VK_FORMAT_B8G8R8A8_UNORM;
            colorSpace = surfaceFormats.get(0).colorSpace();
            for (int i = 0; i < numFormats; i++) {
                VkSurfaceFormatKHR surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_UNORM &&
                        surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }
        ...
    }
    ...
}
```

Finally, we need also to modify the `Image` class to store image dimensions and provide the associated *getters*:

```java
public class Image {
    ...
    private final int height;
    ...
    private final int width;

    public Image(VkCtx vkCtx, ImageData imageData) {
        ...
            this.width = imageData.width;
            this.height = imageData.height;
        ...
    }
    ...
    public int getHeight() {
        return height;
    }
    ...
    public int getWidth() {
        return width;
    }
    ...
}
```

[Next chapter](../chapter-12/chapter-12.md)