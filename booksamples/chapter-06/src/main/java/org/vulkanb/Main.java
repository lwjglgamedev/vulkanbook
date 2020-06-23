package org.vulkanb;

import org.apache.logging.log4j.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;

public class Main implements IAppLogic {

    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        LOGGER.info("Starting application");

        Engine engine = new Engine("Vulkanbook", new Main());
        engine.start();
    }

    @Override
    public void cleanUp() {
        // To be implemented
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMilisec) {
        // To be implemented
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        MeshData meshData = new MeshData("Triangle", new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f},
                new int[]{0, 1, 2});
        render.loadMeshes(new MeshData[]{meshData});
    }
}
