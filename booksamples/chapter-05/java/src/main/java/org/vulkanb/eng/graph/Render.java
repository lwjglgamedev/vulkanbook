package org.vulkanb.eng.graph;

import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

public class Render {

    private CommandPool commandPool;
    private Device device;
    private ForwardRenderActivity fwdRenderActivity;
    private Queue.GraphicsQueue graphQueue;
    private Instance instance;
    private PhysicalDevice physicalDevice;
    private Queue.PresentQueue presentQueue;
    private Surface surface;
    private SwapChain swapChain;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
        physicalDevice = PhysicalDevice.createPhysicalDevice(instance, engProps.getPhysDeviceName());
        device = new Device(physicalDevice);
        surface = new Surface(physicalDevice, window.getWindowHandle());
        graphQueue = new Queue.GraphicsQueue(device, 0);
        presentQueue = new Queue.PresentQueue(device, surface, 0);
        swapChain = new SwapChain(device, surface, window, engProps.getRequestedImages(), engProps.isvSync(),
                presentQueue, new Queue[]{graphQueue});
        commandPool = new CommandPool(device, graphQueue.getQueueFamilyIndex());
        fwdRenderActivity = new ForwardRenderActivity(swapChain, commandPool);
    }

    public void cleanup() {
        presentQueue.waitIdle();
        graphQueue.waitIdle();
        device.waitIdle();
        fwdRenderActivity.cleanup();
        commandPool.cleanup();
        swapChain.cleanup();
        surface.cleanup();
        device.cleanup();
        physicalDevice.cleanup();
        instance.cleanup();
    }

    public void render(Window window, Scene scene) {
        swapChain.acquireNextImage();

        fwdRenderActivity.submit(graphQueue);

        swapChain.presentImage(presentQueue);
    }
}
