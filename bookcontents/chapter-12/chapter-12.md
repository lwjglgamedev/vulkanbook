# Chapter 12 - GUI

In this chapter we will add the capability to display Graphical User Interfaces (GUI) on top of the rendered scene. We will use the [Dear ImGui library](https://github.com/ocornut/imgui) through the [imgui-java](https://github.com/SpaiR/imgui-java) wrapper. ImGui is a light-weight GUI library render-agnostic which can be used with OpenGL, DirectX or Vulkan. We can construct complex GUIs, capable of reacting to user input, and get its output as vertex buffers which we can render in our application as other regular shape. The purpose of this chapter is not to explain ImGui deeply, but to show how can be integrated with our Vulkan based render pipeline.

You can find the complete source code for this chapter [here](../../booksamples/chapter-12).

## Imgui-java dependencies

The first step is to add the [imgui-java](https://github.com/SpaiR/imgui-java) dependencies in the project's `pom.xml` file (The `imgui-java.version` property is defined in the root project `poml.xml` file):

```xml
<?xml version="1.0" encoding="UTF-8"?>
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>io.github.spair</groupId>
            <artifactId>imgui-java-binding</artifactId>
            <version>${imgui-java.version}</version>
        </dependency>
        ...
        <dependency>
            <groupId>io.github.spair</groupId>
            <artifactId>imgui-java-${native.target}</artifactId>
            <version>${imgui-java.version}</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
```

## Render the GUI

In this case, we will be rendering the GUI elements over the scene. Since we will not be applying any post effects to the GUI, such as lighting, we will render them just after the lighting phase has been completed. In order to do that, we will handle GUI rendering in a new class named `GuiRender`, which starts like this:
```java
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
    ...
}
```

The constructor is quite similar to the other `*Render` classes. In this case, we receive as an input a color attachment where will be rendering. This will be the 
attachment used for post processing. We will not use depth attachments in this case, since GUI elements are essentially 2D shapes. We need to cerate render info,
shader modules, the associated pipelines and we will use arrays to sre vertices and indices buffers to store ImGui render results. For each frame, ImGui will generate
the vertices and indices associated to the GUI elements we need to render. At the end of the constructor you will see that wee set a char callback and add a key
callback to the `KeyboardInput` instance. We need to do this in order to handle keyboard input in ImGui widgets, we will see the definition of the `GuiUtils` class later on.

The "usual" methods are defined like this:

```java
public class GuiRender {
    ...
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
                PostRender.COLOR_FORMAT)
                .setPushConstRanges(
                        new PushConstRange[]{
                                new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.VEC2_SIZE)
                        })
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
    ...
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
    ...
}
```

We need to use a different vertex buffer structure, which will be defined in the `GuiVtxBuffStruct` class. ImGui vertices are defined by two coordinates (`x` and `y`), the texture coordinates and a color (in RGBA format), therefore we cannot use the same structure used for the scene models. The `GuiVtxBuffStruct` class is defined like this:
```java
package org.vulkanb.eng.graph.gui;

import org.lwjgl.vulkan.*;
import org.vulkanb.eng.graph.vk.VkUtils;

import static org.lwjgl.vulkan.VK13.*;

public class GuiVtxBuffStruct {

    public static final int VERTEX_SIZE = VkUtils.FLOAT_SIZE * 5;
    private static final int NUMBER_OF_ATTRIBUTES = 3;

    private final VkPipelineVertexInputStateCreateInfo vi;
    private final VkVertexInputAttributeDescription.Buffer viAttrs;
    private final VkVertexInputBindingDescription.Buffer viBindings;

    public GuiVtxBuffStruct() {
        viAttrs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        viBindings = VkVertexInputBindingDescription.calloc(1);
        vi = VkPipelineVertexInputStateCreateInfo.calloc();

        int i = 0;
        int offset = 0;
        // Position
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(offset);

        // Texture coordinates
        i++;
        offset += VkUtils.FLOAT_SIZE * 2;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R32G32_SFLOAT)
                .offset(offset);

        // Color
        i++;
        offset += VkUtils.FLOAT_SIZE * 2;
        viAttrs.get(i)
                .binding(0)
                .location(i)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .offset(offset);

        viBindings.get(0)
                .binding(0)
                .stride(VERTEX_SIZE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        vi
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(viBindings)
                .pVertexAttributeDescriptions(viAttrs);
    }

    public void cleanup() {
        viBindings.free();
        viAttrs.free();
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
```

In the constructor you may have noticed that we are using a method named `initUI` where we initialize the resources required by ImGui:
```java
public class GuiRender {
    ...
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
    ...
}
```
In the `initUI` method, we first need to call the `ImGui` `createContext` function. This should be the very first call prior to invoking any other ImGui functions.
After that, we retrieve a reference to the `ImGuiIO`, and call the `setIniFilename` method. The reason for doing that, is that ImGui, by default, will create a file named
`imgui.ini` when the context is destroyed. This `imgui.ini` file will hold the position and size of the GUI elements, so they can be restored to their last positions. We will
not be using that feature, so we set `null` as the parameter of the `setIniFilename` to deactivate it. After that, we set the display size and a scale. The next step is to
load the texture that will be used to render fonts in a `Texture` instance which will also need to be transitioned to the adequate final layout.

We will add a new method named `loadTextures` which will be used to load textures that we need to use in the GUI (in addition to the one used for text rendering). Imgui
can associate opaque handles (`long`) to widgets that can display images which refer to a unique identifier associated to a texture. In our case, we will use those handles
to store an identifier which later on we can associate to a descriptor set that we will bind while rendering. The `loadTextures` method is defined like this:

```java
public class GuiRender {
    ...
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
    ...
}
```

As you can see, we receive a list of `GuiTexture` instances (we will see the definition later on), and the `TextureCache`. For each of these `GuiTexture` instances, we
create a texture and associate it to a descriptor. We store in the `guiTexturesMap` the `GuiTexture` identifier along the associated descriptor set.

The `GuiTexture` class is defined like this:

```java
package org.vulkanb.eng;

import java.security.SecureRandom;

public record GuiTexture(long id, String texturePath) {
    public GuiTexture(String texturePath) {
        this(getId(), texturePath);
    }

    private static long getId() {
        SecureRandom secureRandom = new SecureRandom();
        long id = Math.abs(secureRandom.nextLong());
        if (id == 0) {
            id += 1;
        }
        return id;
    }
}
```

As you can see is basically a wrapper around a texture path and an automatically generated identifier which is created randomly and always needs to be greater than `0`(more
on this later on). `GuiTexture` instances will be cerated during model creation in the `init` method of the `IGameLogic` implementation. Therefore, we will include them
in the `InitData` class:

```java
package org.vulkanb.eng;

import org.vulkanb.eng.model.*;

import java.util.List;

public record InitData(List<ModelData> models, List<MaterialData> materials, List<GuiTexture> guiTextures) {
}
```

Going back to the `GuiRender` class, the `render` method is defined like this:

```java
public class GuiRender {
    ...
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
    ...
}
```

The first thing that we do is to update the buffers that we will used to store vertices and indices. We will use separate buffers per frames in flight. We will use
just a single buffer (one for vertices and one for indices) when rendering the GUI elements (we will see later on how this is done). One important thing, is that we have
not initialized those buffers in the constructors, so they can be null. In fact, as long as there is no GUI information to be rendered they will stay in that state.
Therefore, if they are null, there is nothing to be done, so we just return. We then just do the usual steps, begin the render, bind the pipeline, set the view port and
bind the vertex and indices buffers. Then, we set push constants to pass a scaling factor (composed by two floats). ImGui will use a coordinate system which sets `(0, 0)` at the top left corner of the screen and `(width, height)` as the the bottom right corner, being `width` and `height` the values set in the `ImGuiIO` `setDisplaySize` method call. We need to transform from that coordinate system to one in the range of `[-1, 1]` for x and y axis, which will be done in the vertex shader with the help of that scaling factor. After that, we iterate over the ImGui draw data, which will help us to set the proper offsets over the vertices and indices buffers when calling `vkCmdDrawIndexed`.
We also restrict the drawing area using the `vkCmdSetScissor` for each of the GUI elements to be rendered. For each of these elements, we will check if it has a specific
texture or no. If the GUI element has defined a texture it will contain a value different than `0` in the opaque handler element retrieved by `getCmdListCmdBufferTextureId`.
With that information and the help of the `guiTexturesMap` we can properly set the proper descriptor set.

> [!WARNING]  
> ImGui applies gamma correction by its own, so it is very important to render to a format that does not apply also gamma correction automatically to avoid applying
> it twice. If you cannot do it, for example, because you are rendering directly to swap chain images. You can use different image views for the same swap chain
> image and use different formats. You may probably need to enable the `VK_KHR_multiview` extension in this case.

Let us review now the `updateBuffers` method:
```java
public class GuiRender {
    ...
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
        if (vtxBuffer == null || vertexBufferSize > vtxBuffer.getRequestedSize()) {
            if (vtxBuffer != null) {
                vtxBuffer.cleanup(vkCtx);
            }
            vtxBuffer = new VkBuffer(vkCtx, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            buffsVtx[idx] = vtxBuffer;
        }

        var indicesBuffer = buffsIdx[idx];
        if (indicesBuffer == null || indexBufferSize > indicesBuffer.getRequestedSize()) {
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
    ...
}
```

As it has been shown above, we will have two set of buffers, one for the vertices and the other one for the indices. We will have as many buffers frames in flight we have,
We first check if `imDrawData.ptr` is null, which may be caused because we have not initialized the GUI and retrieve the total number of vertices and indices to be drawn.
If there are noe vertices or no indices, we just return. required by the GUI defined by the `IGuiInstance` implementation. If the buffers have not been created yet or the number of vertices and indices they hold are less than the already allocated (this means that the GUI elements have changed and we need more space), we create new
Vulkan buffers. The buffers need to be accessed from both the GPU and the application. After that, we just copy the vertices and indices data to those buffers. Once the data has been copied, we call a new method in the `VulkanBuffer` class named `flush`. The rationale for that is that we have not used the `VK_MEMORY_PROPERTY_HOST_COHERENT_BIT` flag when creating the Vulkan buffers. Therefore, we need to ensure that the buffer data is available to the GPU by calling the `flush` method in the `VulkanBuffer` class.

The `GuiRender` class also defines a `resize` method that should be invoked when the render area changes its size. In this methods, we just update the ImGui display size:
```java
public class GuiRender {
    ...
    public void resize(VkCtx vkCtx, Attachment dstAttachment) {
        ImGuiIO imGuiIO = ImGui.getIO();
        VkExtent2D swapChainExtent = vkCtx.getSwapChain().getSwapChainExtent();
        imGuiIO.setDisplaySize(swapChainExtent.width(), swapChainExtent.height());

        renderInfo.free();
        attInfoColor.free();
        attInfoColor = createColorAttachmentInfo(dstAttachment);
        renderInfo = createRenderInfo(dstAttachment, attInfoColor);
    }
    ...
}
```

We have used the `GuiUtils` class which contains implementations for key and char callbacks:

```java
package org.vulkanb.eng.graph.gui;

import imgui.*;
import imgui.flag.ImGuiKey;
import org.lwjgl.glfw.*;

import static org.lwjgl.glfw.GLFW.*;

public class GuiUtils {
    private GuiUtils() {
        // Utility class
    }

    private static int getImKey(int key) {
        return switch (key) {
            case GLFW_KEY_TAB -> ImGuiKey.Tab;
            case GLFW_KEY_LEFT -> ImGuiKey.LeftArrow;
            case GLFW_KEY_RIGHT -> ImGuiKey.RightArrow;
            case GLFW_KEY_UP -> ImGuiKey.UpArrow;
            case GLFW_KEY_DOWN -> ImGuiKey.DownArrow;
            case GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp;
            case GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown;
            case GLFW_KEY_HOME -> ImGuiKey.Home;
            case GLFW_KEY_END -> ImGuiKey.End;
            case GLFW_KEY_INSERT -> ImGuiKey.Insert;
            case GLFW_KEY_DELETE -> ImGuiKey.Delete;
            case GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace;
            case GLFW_KEY_SPACE -> ImGuiKey.Space;
            case GLFW_KEY_ENTER -> ImGuiKey.Enter;
            case GLFW_KEY_ESCAPE -> ImGuiKey.Escape;
            case GLFW_KEY_APOSTROPHE -> ImGuiKey.Apostrophe;
            case GLFW_KEY_COMMA -> ImGuiKey.Comma;
            case GLFW_KEY_MINUS -> ImGuiKey.Minus;
            case GLFW_KEY_PERIOD -> ImGuiKey.Period;
            case GLFW_KEY_SLASH -> ImGuiKey.Slash;
            case GLFW_KEY_SEMICOLON -> ImGuiKey.Semicolon;
            case GLFW_KEY_EQUAL -> ImGuiKey.Equal;
            case GLFW_KEY_LEFT_BRACKET -> ImGuiKey.LeftBracket;
            case GLFW_KEY_BACKSLASH -> ImGuiKey.Backslash;
            case GLFW_KEY_RIGHT_BRACKET -> ImGuiKey.RightBracket;
            case GLFW_KEY_GRAVE_ACCENT -> ImGuiKey.GraveAccent;
            case GLFW_KEY_CAPS_LOCK -> ImGuiKey.CapsLock;
            case GLFW_KEY_SCROLL_LOCK -> ImGuiKey.ScrollLock;
            case GLFW_KEY_NUM_LOCK -> ImGuiKey.NumLock;
            case GLFW_KEY_PRINT_SCREEN -> ImGuiKey.PrintScreen;
            case GLFW_KEY_PAUSE -> ImGuiKey.Pause;
            case GLFW_KEY_KP_0 -> ImGuiKey.Keypad0;
            case GLFW_KEY_KP_1 -> ImGuiKey.Keypad1;
            case GLFW_KEY_KP_2 -> ImGuiKey.Keypad2;
            case GLFW_KEY_KP_3 -> ImGuiKey.Keypad3;
            case GLFW_KEY_KP_4 -> ImGuiKey.Keypad4;
            case GLFW_KEY_KP_5 -> ImGuiKey.Keypad5;
            case GLFW_KEY_KP_6 -> ImGuiKey.Keypad6;
            case GLFW_KEY_KP_7 -> ImGuiKey.Keypad7;
            case GLFW_KEY_KP_8 -> ImGuiKey.Keypad8;
            case GLFW_KEY_KP_9 -> ImGuiKey.Keypad9;
            case GLFW_KEY_KP_DECIMAL -> ImGuiKey.KeypadDecimal;
            case GLFW_KEY_KP_DIVIDE -> ImGuiKey.KeypadDivide;
            case GLFW_KEY_KP_MULTIPLY -> ImGuiKey.KeypadMultiply;
            case GLFW_KEY_KP_SUBTRACT -> ImGuiKey.KeypadSubtract;
            case GLFW_KEY_KP_ADD -> ImGuiKey.KeypadAdd;
            case GLFW_KEY_KP_ENTER -> ImGuiKey.KeypadEnter;
            case GLFW_KEY_KP_EQUAL -> ImGuiKey.KeypadEqual;
            case GLFW_KEY_LEFT_SHIFT -> ImGuiKey.LeftShift;
            case GLFW_KEY_LEFT_CONTROL -> ImGuiKey.LeftCtrl;
            case GLFW_KEY_LEFT_ALT -> ImGuiKey.LeftAlt;
            case GLFW_KEY_LEFT_SUPER -> ImGuiKey.LeftSuper;
            case GLFW_KEY_RIGHT_SHIFT -> ImGuiKey.RightShift;
            case GLFW_KEY_RIGHT_CONTROL -> ImGuiKey.RightCtrl;
            case GLFW_KEY_RIGHT_ALT -> ImGuiKey.RightAlt;
            case GLFW_KEY_RIGHT_SUPER -> ImGuiKey.RightSuper;
            case GLFW_KEY_MENU -> ImGuiKey.Menu;
            case GLFW_KEY_0 -> ImGuiKey._0;
            case GLFW_KEY_1 -> ImGuiKey._1;
            case GLFW_KEY_2 -> ImGuiKey._2;
            case GLFW_KEY_3 -> ImGuiKey._3;
            case GLFW_KEY_4 -> ImGuiKey._4;
            case GLFW_KEY_5 -> ImGuiKey._5;
            case GLFW_KEY_6 -> ImGuiKey._6;
            case GLFW_KEY_7 -> ImGuiKey._7;
            case GLFW_KEY_8 -> ImGuiKey._8;
            case GLFW_KEY_9 -> ImGuiKey._9;
            case GLFW_KEY_A -> ImGuiKey.A;
            case GLFW_KEY_B -> ImGuiKey.B;
            case GLFW_KEY_C -> ImGuiKey.C;
            case GLFW_KEY_D -> ImGuiKey.D;
            case GLFW_KEY_E -> ImGuiKey.E;
            case GLFW_KEY_F -> ImGuiKey.F;
            case GLFW_KEY_G -> ImGuiKey.G;
            case GLFW_KEY_H -> ImGuiKey.H;
            case GLFW_KEY_I -> ImGuiKey.I;
            case GLFW_KEY_J -> ImGuiKey.J;
            case GLFW_KEY_K -> ImGuiKey.K;
            case GLFW_KEY_L -> ImGuiKey.L;
            case GLFW_KEY_M -> ImGuiKey.M;
            case GLFW_KEY_N -> ImGuiKey.N;
            case GLFW_KEY_O -> ImGuiKey.O;
            case GLFW_KEY_P -> ImGuiKey.P;
            case GLFW_KEY_Q -> ImGuiKey.Q;
            case GLFW_KEY_R -> ImGuiKey.R;
            case GLFW_KEY_S -> ImGuiKey.S;
            case GLFW_KEY_T -> ImGuiKey.T;
            case GLFW_KEY_U -> ImGuiKey.U;
            case GLFW_KEY_V -> ImGuiKey.V;
            case GLFW_KEY_W -> ImGuiKey.W;
            case GLFW_KEY_X -> ImGuiKey.X;
            case GLFW_KEY_Y -> ImGuiKey.Y;
            case GLFW_KEY_Z -> ImGuiKey.Z;
            case GLFW_KEY_F1 -> ImGuiKey.F1;
            case GLFW_KEY_F2 -> ImGuiKey.F2;
            case GLFW_KEY_F3 -> ImGuiKey.F3;
            case GLFW_KEY_F4 -> ImGuiKey.F4;
            case GLFW_KEY_F5 -> ImGuiKey.F5;
            case GLFW_KEY_F6 -> ImGuiKey.F6;
            case GLFW_KEY_F7 -> ImGuiKey.F7;
            case GLFW_KEY_F8 -> ImGuiKey.F8;
            case GLFW_KEY_F9 -> ImGuiKey.F9;
            case GLFW_KEY_F10 -> ImGuiKey.F10;
            case GLFW_KEY_F11 -> ImGuiKey.F11;
            case GLFW_KEY_F12 -> ImGuiKey.F12;
            default -> ImGuiKey.None;
        };
    }


    public static class CharCallBack implements GLFWCharCallbackI {
        @Override
        public void invoke(long windowHandle, int c) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureKeyboard()) {
                return;
            }
            io.addInputCharacter(c);
        }
    }

    public static class KeyCallback implements GLFWKeyCallbackI {
        @Override
        public void invoke(long windowHandle, int key, int scancode, int action, int mods) {
            ImGuiIO io = ImGui.getIO();
            if (!io.getWantCaptureKeyboard()) {
                return;
            }
            if (action == GLFW_PRESS) {
                io.addKeyEvent(getImKey(key), true);
            } else if (action == GLFW_RELEASE) {
                io.addKeyEvent(getImKey(key), false);
            }
        }
    }
}
```

The key callback first check if ImGui needs to capture keyboard (that is, the focus is om some ImGui window / widget). If so, we set up the state of ImGui according to key pressed or released events. We need to "translate" GLFW key codes to ImGui ones which is done in the `getImKey` method. The char call back is required also for text input widgets can process those events.

The vertex shader used for rendering the GUI (`gui_vtx.glsl`) is quite simple, we just transform the coordinates so they are in the `[-1, 1]` range and output the texture coordinates and color so they can be used in the fragment shader:

```glsl
#version 450

layout (location = 0) in vec2 inPos;
layout (location = 1) in vec2 inTextCoords;
layout (location = 2) in vec4 inColor;

layout (push_constant) uniform PushConstants {
    vec2 scale;
} pushConstants;

layout (location = 0) out vec2 outTextCoords;
layout (location = 1) out vec4 outColor;

out gl_PerVertex
{
    vec4 gl_Position;
};

void main()
{
    outTextCoords = inTextCoords;
    outColor = inColor;
    gl_Position = vec4(inPos * pushConstants.scale + vec2(-1.0, 1.0), 0.0, 1.0);
}
```

In the fragment shader (`gui_frg.glsl`) we just output the combination of the vertex color and the texture color associated to its texture coordinates:
```glsl
#version 450

layout (location = 0) in vec2 inTextCoords;
layout (location = 1) in vec4 inColor;

layout (binding = 0) uniform sampler2D fontsSampler;

layout (location = 0) out vec4 outFragColor;

void main()
{
    outFragColor = inColor  * texture(fontsSampler, inTextCoords);
}
```

## Complete the changes

Now we need to put the new `GuiRender` class into play, so we will start with the changes in the `Render` class:
```java
public class Render {
    ...
    private final GuiRender guiRender;
    ...
    public Render(EngCtx engCtx) {
        ...
        guiRender = new GuiRender(engCtx, vkCtx, graphQueue, postRender.getAttachment());
        ...

    }

    public void cleanup() {
        ...
        guiRender.cleanup(vkCtx);
        ...
    }
    ...
    public void init(InitData initData) {
        ...
        List<GuiTexture> guiTextures = initData.guiTextures();
        if (guiTextures != null) {
            initData.guiTextures().forEach(e -> textureCache.addTexture(vkCtx, e.texturePath(), e.texturePath(),
                    VK_FORMAT_R8G8B8A8_SRGB));
        }
        ...
        guiRender.loadTextures(vkCtx, initData.guiTextures(), textureCache);
    }
    ...
    public void render(EngCtx engCtx) {
        ...
        scnRender.render(engCtx, vkCtx, cmdBuffer, modelsCache, materialsCache, currentFrame);
        postRender.render(vkCtx, cmdBuffer, scnRender.getAttColor());
        guiRender.render(vkCtx, cmdBuffer, currentFrame, postRender.getAttachment());
        ...
    }

    private void resize(Window window) {
        ...
        guiRender.resize(vkCtx, postRender.getAttachment());
        ...
    }
}
```

We need to instantiate the `GuiRender` class in the `Render` constructor and free it in the `cleanup` method. We need to update also the `init`  method to, first, include
them in the cache and then invoke `loadTextures` over the `Render` class to create the proper descriptor sets. In the `render` method we just call the `GuiRender` 
`render` method after we have finished with post processing.

The final step is to modify the `Main` class:

```java
public class Main implements IGameLogic {
    ...
    private boolean defaultGui = true;
    private GuiTexture guiTexture;
    ...
    private boolean handleGui(EngCtx engCtx) {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = engCtx.window().getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.addMousePosEvent(mousePos.x, mousePos.y);
        imGuiIO.addMouseButtonEvent(0, mouseInput.isLeftButtonPressed());
        imGuiIO.addMouseButtonEvent(1, mouseInput.isRightButtonPressed());

        if (defaultGui) {
            ImGui.newFrame();
            ImGui.showDemoWindow();
            ImGui.endFrame();
            ImGui.render();
        } else {
            ImGui.newFrame();
            ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
            ImGui.setNextWindowSize(200, 200);
            ImGui.begin("Test Window");
            ImGui.image(guiTexture.id(), new ImVec2(300, 300));
            ImGui.end();
            ImGui.endFrame();
            ImGui.render();
        }

        return imGuiIO.getWantCaptureKeyboard();
    }

    @Override
    public InitData init(EngCtx engCtx) {
        ...
        guiTexture = new GuiTexture("resources/textures/vulkan.png");
        List<GuiTexture> guiTextures = new ArrayList<>();
        guiTextures.add(guiTexture);
        ...
        return new InitData(models, materials, guiTextures);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        if (handleGui(engCtx)) {
            return;
        }
        ...
        if (ki.keyPressed(GLFW_KEY_1)) {
            defaultGui = true;
        } else if (ki.keyPressed(GLFW_KEY_2)) {
            defaultGui = false;
        }
        ...
    }
    ...
}
```

We have two possible GUI Windows to show, the default IMgui demo window and a simple onw which hows a texture. Therefore, we need to load that sample texture. In the `input`
method we also checc if the input has been handled by ImGui and return uin this case to avoid double handling of users input.The final result is shown in the next figure.

<img src="rc12-screen-shot.png" title="" alt="Screen Shot" data-align="center">

[Next chapter](../chapter-13/chapter-13.md)