package org.vulkanb.eng.graph;

import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.geometry.GeometryRenderActivity;
import org.vulkanb.eng.graph.lighting.LightingRenderActivity;
import org.vulkanb.eng.graph.shadows.ShadowRenderActivity;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Render {

    private final CommandPool commandPool;
    private final Device device;
    private final GeometryRenderActivity geometryRenderActivity;
    private final Queue.GraphicsQueue graphQueue;
    private final Instance instance;
    private final LightingRenderActivity lightingRenderActivity;
    private final PhysicalDevice physicalDevice;
    private final PipelineCache pipelineCache;
    private final Queue.PresentQueue presentQueue;
    private final ShadowRenderActivity shadowRenderActivity;
    private final Surface surface;
    private final TextureCache textureCache;
    private final List<VulkanModel> vulkanModels;

    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(instance, physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(), engProps.isvSync(),
                presentQueue, new Queue[]{graphQueue});
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        vulkanModels = new ArrayList<>();
        textureCache = new TextureCache();
        geometryRenderActivity = new GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene);
        shadowRenderActivity = new ShadowRenderActivity(swapChain, pipelineCache, scene);
        List<Attachment> attachments = new ArrayList<>(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity = new LightingRenderActivity(swapChain, commandPool, pipelineCache, attachments, scene);
    }

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        textureCache.cleanup();
        vulkanModels.forEach(VulkanModel::cleanup);
        pipelineCache.cleanup();
        lightingRenderActivity.cleanup();
        shadowRenderActivity.cleanup();
        geometryRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void loadModels(List<ModelData> modelDataList) {
        Logger.debug("Loading {} model(s)", modelDataList.size());
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, textureCache, commandPool, graphQueue));
        Logger.debug("Loaded {} model(s)", modelDataList.size());

        geometryRenderActivity.registerModels(vulkanModels);
        shadowRenderActivity.registerModels(vulkanModels);
    }

    public void render(Window window, Scene scene) {
        if (window.getWidth() <= 0 && window.getHeight() <= 0) {
            return;
        }
        geometryRenderActivity.waitForFence();
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        CommandBuffer commandBuffer = geometryRenderActivity.beginRecording();
        geometryRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels);
        shadowRenderActivity.recordCommandBuffer(commandBuffer, vulkanModels);
        geometryRenderActivity.endRecording(commandBuffer);
        geometryRenderActivity.submit(graphQueue);
        lightingRenderActivity.prepareCommandBuffer(shadowRenderActivity.getShadowCascades());
        lightingRenderActivity.submit(graphQueue);

        if (swapChain.presentImage(presentQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();

        device.waitIdle();
        graphQueue.waitIdle();

        swapChain.cleanup();

        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(), engProps.isvSync(),
                presentQueue, new Queue[]{graphQueue});
        geometryRenderActivity.resize(swapChain);
        shadowRenderActivity.resize(swapChain);
        List<Attachment> attachments = new ArrayList<>(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity.resize(swapChain, attachments);
    }
}
