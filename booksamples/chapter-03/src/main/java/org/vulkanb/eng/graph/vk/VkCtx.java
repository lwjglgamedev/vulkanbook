package org.vulkanb.eng.graph.vk;

import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.wnd.Window;

public class VkCtx {

    private final Device device;
    private final Instance instance;
    private final PhysDevice physDevice;
    private Surface surface;

    public VkCtx(Window window) {
        var engCfg = EngCfg.getInstance();
        instance = new Instance(engCfg.isVkValidate());
        physDevice = PhysDevice.createPhysicalDevice(instance, engCfg.getPhysDeviceName());
        device = new Device(physDevice);
        surface = new Surface(instance, physDevice, window);
    }

    public void cleanup() {
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

    public Surface getSurface() {
        return surface;
    }
}