package org.vulkanb.eng.graph;

import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

public class Render {

    private final Device device;
    private final Queue.GraphicsQueue graphQueue;
    private final Instance instance;
    private final PhysicalDevice physicalDevice;
    private final Surface surface;

    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
    }

    public void cleanup() {
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void render(Window window, Scene scene) {
        // To be implemented
    }
}
