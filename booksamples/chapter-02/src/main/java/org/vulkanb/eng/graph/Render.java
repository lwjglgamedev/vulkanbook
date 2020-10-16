package org.vulkanb.eng.graph;

import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.vk.Instance;
import org.vulkanb.eng.scene.Scene;

public class Render {

    private final Instance instance;

    public Render(Window window, Scene scene) {
        EngineProperties engProps = EngineProperties.getInstance();
        instance = new Instance(engProps.isValidate());
    }

    public void cleanup() {
        instance.cleanup();
    }

    public void render(Window window, Scene scene) {
        // To be implemented
    }
}
