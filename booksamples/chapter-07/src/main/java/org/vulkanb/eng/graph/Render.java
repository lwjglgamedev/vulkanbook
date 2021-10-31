package org.vulkanb.eng.graph;

import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.Queue;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Render {

    private final CommandPool commandPool;
    private final Device device;
    private final ForwardRenderActivity fwdRenderActivity;
    private final Queue.GraphicsQueue graphQueue;
    private final Instance instance;
    private final PhysicalDevice physicalDevice;
    private final PipelineCache pipelineCache;
    private final Queue.PresentQueue presentQueue;
    private final Surface surface;
    private final List<VulkanModel> vulkanModels;

    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        pipelineCache = new PipelineCache(device);
        fwdRenderActivity = new ForwardRenderActivity(swapChain, commandPool, pipelineCache, scene);
        vulkanModels = new ArrayList<>();
    }

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        vulkanModels.forEach(VulkanModel::cleanup);
        pipelineCache.cleanup();
        fwdRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void loadModels(List<ModelData> modelDataList) {
        Logger.debug("Loading {} model(s)", modelDataList.size());
        vulkanModels.addAll(VulkanModel.transformModels(modelDataList, commandPool, graphQueue));
        Logger.debug("Loaded {} model(s)", modelDataList.size());
    }

    public void render(Window window, Scene scene) {
        if (window.getWidth() <= 0 && window.getHeight() <= 0) {
            return;
        }
        if (window.isResized() || swapChain.acquireNextImage()) {
            window.resetResized();
            resize(window);
            scene.getProjection().resize(window.getWidth(), window.getHeight());
            swapChain.acquireNextImage();
        }

        fwdRenderActivity.recordCommandBuffer(vulkanModels);
        fwdRenderActivity.submit(presentQueue);

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
        fwdRenderActivity.resize(swapChain);
    }
}