package org.vulkanb.eng.graph.vk;

import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.wnd.Window;

public class VkCtx {

    private final Device device;
    private final Instance instance;
    private final PhysDevice physDevice;
    private final PipelineCache pipelineCache;
    private Surface surface;
    private SwapChain swapChain;

    public VkCtx(Window window) {
        var engCfg = EngCfg.getInstance();
        instance = new Instance(engCfg.isVkValidate());
        physDevice = PhysDevice.createPhysicalDevice(instance, engCfg.getPhysDeviceName());
        device = new Device(physDevice);
        surface = new Surface(instance, physDevice, window);
        swapChain = new SwapChain(window, device, surface, engCfg.getRequestedImages(), engCfg.getVSync());
        pipelineCache = new PipelineCache(device);
    }

    public void cleanup() {
        pipelineCache.cleanup(device);
        swapChain.cleanup(device);
        surface.cleanup(instance);
        device.cleanup();
        physDevice.cleanup();
        instance.cleanup();
    }

    public Device getDevice() {
        return device;
    }

    public PhysDevice getPhysDevice() {
        return physDevice;
    }

    public PipelineCache getPipelineCache() {
        return pipelineCache;
    }

    public Surface getSurface() {
        return surface;
    }

    public SwapChain getSwapChain() {
        return swapChain;
    }

    public void resize(Window window) {
        swapChain.cleanup(device);
        surface.cleanup(instance);
        var engCfg = EngCfg.getInstance();
        surface = new Surface(instance, physDevice, window);
        swapChain = new SwapChain(window, device, surface, engCfg.getRequestedImages(), engCfg.getVSync());
    }
}
