package org.vulkanb.eng.graph;

import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.*;
import org.vulkanb.eng.scene.Scene;

public class Render {

    private Device device;
    private Instance instance;
    private PhysicalDevice physicalDevice;

    public void cleanUp() {
        this.device.cleanUp();
        this.physicalDevice.cleanUp();
        this.instance.cleanUp();
    }

    public void init(Window window) {
        EngineProperties engProps = EngineProperties.getInstance();
        this.instance = new Instance(engProps.isValidate());
        this.physicalDevice = PhysicalDevice.createPhysicalDevice(this.instance, engProps.getPhysDeviceName());
        this.device = new Device(this.physicalDevice);
    }

    public void render(Window window, Scene scene) {
        // To be implemented
    }
}
