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

    public void cleanUp() {
        this.presentQueue.waitIdle();
        this.graphQueue.waitIdle();
        this.device.waitIdle();
        this.fwdRenderActivity.cleanUp();
        this.commandPool.cleanUp();
        this.swapChain.cleanUp();
        this.surface.cleanUp();
        this.device.cleanUp();
        this.physicalDevice.cleanUp();
        this.instance.cleanUp();
    }

    public void init(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();
        this.instance = new Instance(engProps.isValidate());
        this.physicalDevice = PhysicalDevice.createPhysicalDevice(this.instance, engProps.getPhysDeviceName());
        this.device = new Device(this.physicalDevice);
        this.surface = new Surface(this.physicalDevice, window.getWindowHandle());
        this.graphQueue = new Queue.GraphicsQueue(this.device, 0);
        this.presentQueue = new Queue.PresentQueue(this.device, this.surface, 0);
        this.swapChain = new SwapChain(this.device, this.surface, window, engProps.getRequestedImages(),
                engProps.isvSync());
        this.commandPool = new CommandPool(this.device, this.graphQueue.getQueueFamilyIndex());
        this.fwdRenderActivity = new ForwardRenderActivity(this.swapChain, this.commandPool);
    }

    public void render(Window window, Scene scene) {
        this.swapChain.acquireNextImage();

        this.fwdRenderActivity.submit(this.presentQueue);

        this.swapChain.presentImage(this.graphQueue);
    }
}
