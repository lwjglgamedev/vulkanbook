package org.vulkanb;

import org.apache.logging.log4j.*;
import org.joml.Vector3f;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;

public class Main implements IAppLogic {

    private static final Logger LOGGER = LogManager.getLogger();

    private float angle = 0;
    private Entity cubeEntity;
    private Vector3f rotatingAngle = new Vector3f(1, 1, 1);

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
        angle += 1.0f;
        if (angle >= 360) {
            angle = angle - 360;
        }
        this.cubeEntity.getRotation().identity().rotateAxis((float) Math.toRadians(angle), rotatingAngle);
        this.cubeEntity.updateModelMatrix();
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        String meshId = "CubeMesh";
        MeshData[] meshDataList = ModelLoader.loadMeshes(meshId, "resources/models/cube/cube.obj",
                "resources/models/cube");
        render.loadMeshes(meshDataList);

        this.cubeEntity = new Entity("CubeEntity", meshId, new Vector3f(0.0f, 0.0f, 0.0f));
        this.cubeEntity.setPosition(0, 0, -2);
        scene.addEntity(this.cubeEntity);
    }
}
