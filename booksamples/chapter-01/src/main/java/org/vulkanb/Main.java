package org.vulkanb;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Main implements IAppLogic {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        LOGGER.info("Starting application");
        Engine engine = new Engine("Vulkan Book", new Main());
        engine.start();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        // To be implemented
    }
}
