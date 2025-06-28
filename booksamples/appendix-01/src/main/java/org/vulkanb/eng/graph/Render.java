package org.vulkanb.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.anim.AnimRender;
import org.vulkanb.eng.graph.gui.GuiRender;
import org.vulkanb.eng.graph.light.LightRender;
import org.vulkanb.eng.graph.post.PostRender;
import org.vulkanb.eng.graph.scn.ScnRender;
import org.vulkanb.eng.graph.shadow.ShadowRender;
import org.vulkanb.eng.graph.swap.SwapChainRender;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.wnd.Window;

import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

public class Render {

    private final AnimRender animRender;
    private final AnimationsCache animationsCache;
    private final CmdBuffer[] cmdBuffers;
    private final CmdPool[] cmdPools;
    private final Fence[] fences;
    private final GlobalBuffers globalBuffers;
    private final Queue.GraphicsQueue graphQueue;
    private final GuiRender guiRender;
    private final Semaphore[] imageAqSemphs;
    private final LightRender lightRender;
    private final MaterialsCache materialsCache;
    private final ModelsCache modelsCache;
    private final PostRender postRender;
    private final Queue.PresentQueue presentQueue;
    private final Semaphore[] renderCompleteSemphs;
    private final ScnRender scnRender;
    private final ShadowRender shadowRender;
    private final SwapChainRender swapChainRender;
    private final TextureCache textureCache;
    private final VkCtx vkCtx;
    private boolean resize;

    public Render(EngCtx engCtx) {
        vkCtx = new VkCtx(engCtx.window());

        graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        presentQueue = new Queue.PresentQueue(vkCtx, 0);

        cmdPools = new CmdPool[VkUtils.MAX_IN_FLIGHT];
        cmdBuffers = new CmdBuffer[VkUtils.MAX_IN_FLIGHT];
        fences = new Fence[VkUtils.MAX_IN_FLIGHT];
        imageAqSemphs = new Semaphore[VkUtils.MAX_IN_FLIGHT];
        int numSwapChainImages = vkCtx.getSwapChain().getNumImages();
        renderCompleteSemphs = new Semaphore[numSwapChainImages];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            cmdPools[i] = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            cmdBuffers[i] = new CmdBuffer(vkCtx, cmdPools[i], true, true);
            fences[i] = new Fence(vkCtx, true);
            imageAqSemphs[i] = new Semaphore(vkCtx);
        }
        for (int i = 0; i < numSwapChainImages; i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx);
        }
        resize = false;
        scnRender = new ScnRender(vkCtx, engCtx);
        shadowRender = new ShadowRender(vkCtx);
        List<Attachment> attachments = new ArrayList<>(scnRender.getMrtAttachments().getColorAttachments());
        attachments.add(shadowRender.getDepthAttachment());
        lightRender = new LightRender(vkCtx, attachments);
        postRender = new PostRender(vkCtx, lightRender.getAttachment());
        guiRender = new GuiRender(engCtx, vkCtx, graphQueue, postRender.getAttachment());
        swapChainRender = new SwapChainRender(vkCtx, postRender.getAttachment());
        animRender = new AnimRender(vkCtx);
        modelsCache = new ModelsCache();
        textureCache = new TextureCache();
        materialsCache = new MaterialsCache();
        animationsCache = new AnimationsCache();
        globalBuffers = new GlobalBuffers();
    }

    public void cleanup() {
        vkCtx.getDevice().waitIdle();

        globalBuffers.cleanup(vkCtx);
        animRender.cleanup(vkCtx);
        scnRender.cleanup(vkCtx);
        postRender.cleanup(vkCtx);
        lightRender.cleanup(vkCtx);
        guiRender.cleanup(vkCtx);
        shadowRender.cleanup(vkCtx);
        swapChainRender.cleanup(vkCtx);

        modelsCache.cleanup(vkCtx);
        textureCache.cleanup(vkCtx);
        materialsCache.cleanup(vkCtx);
        animationsCache.cleanup(vkCtx);

        Arrays.asList(renderCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(imageAqSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(fences).forEach(i -> i.cleanup(vkCtx));
        for (int i = 0; i < cmdPools.length; i++) {
            cmdBuffers[i].cleanup(vkCtx, cmdPools[i]);
            cmdPools[i].cleanup(vkCtx);
        }

        vkCtx.cleanup();
    }

    public void init(EngCtx engCtx, InitData initData) {
        List<MaterialData> materials = initData.materials();
        Logger.debug("Loading {} material(s)", materials.size());
        materialsCache.loadMaterials(vkCtx, materials, textureCache, cmdPools[0], graphQueue);
        Logger.debug("Loaded {} material(s)", materials.size());

        List<GuiTexture> guiTextures = initData.guiTextures();
        if (guiTextures != null) {
            initData.guiTextures().forEach(e -> textureCache.addTexture(vkCtx, e.texturePath(), e.texturePath(),
                    VK_FORMAT_R8G8B8A8_SRGB));
        }

        Logger.debug("Transitioning textures");
        textureCache.transitionTexts(vkCtx, cmdPools[0], graphQueue);
        Logger.debug("Textures transitioned");

        List<ModelData> models = initData.models();
        Logger.debug("Loading {} model(s)", models.size());
        modelsCache.loadModels(vkCtx, models, cmdPools[0], graphQueue);
        Logger.debug("Loaded {} model(s)", models.size());

        scnRender.loadMaterials(vkCtx, materialsCache, textureCache);
        shadowRender.loadMaterials(vkCtx, materialsCache, textureCache);
        guiRender.loadTextures(vkCtx, initData.guiTextures(), textureCache);
        animationsCache.loadAnimations(vkCtx, engCtx.scene().getEntities(), modelsCache);
        animRender.loadModels(modelsCache);
    }

    private void recordingStart(CmdPool cmdPool, CmdBuffer cmdBuffer) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    private void recordingStop(CmdBuffer cmdBuffer) {
        cmdBuffer.endRecording();
    }

    public void render(EngCtx engCtx, int currentRenderFrame) {
        SwapChain swapChain = vkCtx.getSwapChain();

        waitForFence(currentRenderFrame);

        var cmdPool = cmdPools[currentRenderFrame];
        var cmdBuffer = cmdBuffers[currentRenderFrame];

        animRender.render(engCtx, vkCtx, modelsCache, animationsCache);

        recordingStart(cmdPool, cmdBuffer);

        scnRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentRenderFrame);
        shadowRender.render(engCtx, vkCtx, cmdBuffer, globalBuffers, currentRenderFrame);
        lightRender.render(engCtx, vkCtx, cmdBuffer, scnRender.getMrtAttachments(), shadowRender.getDepthAttachment(),
                shadowRender.getCascadeShadows(currentRenderFrame), currentRenderFrame);
        postRender.render(vkCtx, cmdBuffer, lightRender.getAttachment());
        guiRender.render(vkCtx, cmdBuffer, currentRenderFrame, postRender.getAttachment());

        int imageIndex;
        if (resize || (imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), imageAqSemphs[currentRenderFrame])) < 0) {
            resize(engCtx);
            return;
        }

        swapChainRender.render(vkCtx, cmdBuffer, postRender.getAttachment(), imageIndex);

        recordingStop(cmdBuffer);

        submit(cmdBuffer, currentRenderFrame, imageIndex);

        resize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);
    }

    private void resize(EngCtx engCtx) {
        Window window = engCtx.window();
        if (window.getWidth() == 0 && window.getHeight() == 0) {
            return;
        }
        resize = false;
        vkCtx.getDevice().waitIdle();

        vkCtx.resize(window);

        Arrays.asList(renderCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(imageAqSemphs).forEach(i -> i.cleanup(vkCtx));
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            imageAqSemphs[i] = new Semaphore(vkCtx);
        }
        for (int i = 0; i < vkCtx.getSwapChain().getNumImages(); i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx);
        }

        VkExtent2D extent = vkCtx.getSwapChain().getSwapChainExtent();
        engCtx.scene().getProjection().resize(extent.width(), extent.height());
        scnRender.resize(engCtx, vkCtx);
        List<Attachment> attachments = new ArrayList<>(scnRender.getMrtAttachments().getColorAttachments());
        attachments.add(shadowRender.getDepthAttachment());
        lightRender.resize(vkCtx, attachments);
        postRender.resize(vkCtx, lightRender.getAttachment());
        guiRender.resize(vkCtx, postRender.getAttachment());
        swapChainRender.resize(vkCtx, postRender.getAttachment());
    }

    private void submit(CmdBuffer cmdBuff, int currentFrame, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            var fence = fences[currentFrame];
            fence.reset(vkCtx);
            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuff.getVkCommandBuffer());
            VkSemaphoreSubmitInfo.Buffer waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(imageAqSemphs[currentFrame].getVkSemaphore());
            VkSemaphoreSubmitInfo.Buffer signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(renderCompleteSemphs[imageIndex].getVkSemaphore());
            graphQueue.submit(cmds, waitSemphs, signalSemphs, fence);
        }
    }

    public void updateGlobalBuffers(EngCtx engCtx, int currentFrame) {
        globalBuffers.update(vkCtx, engCtx.scene(), modelsCache, animationsCache, materialsCache, currentFrame);
    }

    private void waitForFence(int currentFrame) {
        var fence = fences[currentFrame];
        fence.fenceWait(vkCtx);
    }
}
