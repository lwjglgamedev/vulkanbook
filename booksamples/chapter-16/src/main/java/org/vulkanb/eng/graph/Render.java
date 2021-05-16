package org.vulkanb.eng.graph;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.animation.AnimationComputeActivity;
import org.vulkanb.eng.graph.geometry.GeometryRenderActivity;
import org.vulkanb.eng.graph.gui.GuiRenderActivity;
import org.vulkanb.eng.graph.lighting.LightingRenderActivity;
import org.vulkanb.eng.graph.shadows.ShadowRenderActivity;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Render {

    private static final Logger LOGGER = LogManager.getLogger();

    private final AnimationComputeActivity animationComputeActivity;
    private final CommandPool commandPool;
    private final Device device;
    private final GeometryRenderActivity geometryRenderActivity;
    private final GlobalBuffers globalBuffers;
    private final Queue.GraphicsQueue graphQueue;
    private final GuiRenderActivity guiRenderActivity;
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
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        vulkanModels = new ArrayList<>();
        textureCache = new TextureCache();
        globalBuffers = new GlobalBuffers(device);
        geometryRenderActivity = new GeometryRenderActivity(swapChain, commandPool, pipelineCache, scene, globalBuffers);
        shadowRenderActivity = new ShadowRenderActivity(swapChain, pipelineCache, scene);
        List<Attachment> attachments = new ArrayList<>(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity = new LightingRenderActivity(swapChain, commandPool, pipelineCache, attachments, scene);
        animationComputeActivity = new AnimationComputeActivity(commandPool, pipelineCache, scene);
        guiRenderActivity = new GuiRenderActivity(swapChain, commandPool, graphQueue, pipelineCache,
                lightingRenderActivity.getLightingFrameBuffer());
    }

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        textureCache.cleanup();
        //vulkanModels.forEach(NewVulkanModel::cleanup);
        pipelineCache.cleanup();
        guiRenderActivity.cleanup();
        lightingRenderActivity.cleanup();
        animationComputeActivity.cleanup();
        shadowRenderActivity.cleanup();
        geometryRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        globalBuffers.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void loadAnimation(Entity entity) {
        /*
        String modelId = entity.getModelId();
        Optional<VulkanModel> optModel = vulkanModels.stream().filter(m -> m.getModelId().equals(modelId)).findFirst();
        if (optModel.isEmpty()) {
            throw new RuntimeException("Could not find model [" + modelId + "]");
        }
        VulkanModel vulkanModel = optModel.get();
        if (!vulkanModel.hasAnimations()) {
            throw new RuntimeException("Model [" + modelId + "] does not define animations");
        }

        animationComputeActivity.registerEntity(vulkanModel, entity);
         */
    }

    public void loadModels(List<ModelData> modelDataList) {
        LOGGER.debug("Loading {} model(s)", modelDataList.size());
        vulkanModels.addAll(globalBuffers.loadModels(modelDataList, textureCache, commandPool, graphQueue));
        LOGGER.debug("Loaded {} model(s)", modelDataList.size());

        geometryRenderActivity.loadModels(textureCache);
        //animationComputeActivity.registerModels(vulkanModels);
    }

    public void render(Window window, Scene scene) {
        if (!globalBuffers.isIndirectRecorded()) {
            globalBuffers.loadGameItems(vulkanModels, scene, commandPool, graphQueue);
        }
        if (window.getWidth() <= 0 && window.getHeight() <= 0) {
            return;
        }
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        //animationComputeActivity.recordCommandBuffer(vulkanModels);
        //animationComputeActivity.submit();

        CommandBuffer commandBuffer = geometryRenderActivity.beginRecording();
        geometryRenderActivity.recordCommandBuffer(commandBuffer, animationComputeActivity.getEntityAnimationsBuffers(),
                globalBuffers);
        shadowRenderActivity.recordCommandBuffer(commandBuffer, animationComputeActivity.getEntityAnimationsBuffers(),
                globalBuffers);
        geometryRenderActivity.endRecording(commandBuffer);
        geometryRenderActivity.submit(graphQueue);
        commandBuffer = lightingRenderActivity.beginRecording(shadowRenderActivity.getShadowCascades());
        lightingRenderActivity.recordCommandBuffer(commandBuffer);
        guiRenderActivity.recordCommandBuffer(scene, commandBuffer);
        lightingRenderActivity.endRecording(commandBuffer);
        lightingRenderActivity.submit(graphQueue);

        if (swapChain.presentImage(graphQueue)) {
            window.setResized(true);
        }
    }

    private void resize(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();

        device.waitIdle();
        graphQueue.waitIdle();

        swapChain.cleanup();

        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        geometryRenderActivity.resize(swapChain);
        shadowRenderActivity.resize(swapChain);
        List<Attachment> attachments = new ArrayList<>(geometryRenderActivity.getAttachments());
        attachments.add(shadowRenderActivity.getDepthAttachment());
        lightingRenderActivity.resize(swapChain, attachments);
        guiRenderActivity.resize(swapChain);
    }
}