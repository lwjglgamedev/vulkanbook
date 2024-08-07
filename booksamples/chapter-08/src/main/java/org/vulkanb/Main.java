package org.vulkanb;

import org.joml.Vector3f;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Main implements IAppLogic {

    private final Vector3f rotatingAngle = new Vector3f(1, 1, 1);
    private float angle = 0;
    private Entity cubeEntity;

    public static void main(String[] args) {
        Logger.info("Starting application");

        Engine engine = new Engine("Vulkan Book", new Main());
        engine.start();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        List<ModelData> modelDataList = new ArrayList<>();

        String modelId = "CubeModel";
        ModelData modelData = ModelLoader.loadModel(modelId, "resources/models/cube/cube.obj",
                "resources/models/cube");
        modelDataList.add(modelData);
        cubeEntity = new Entity("CubeEntity", modelId, new Vector3f(0.0f, 0.0f, 0.0f));
        cubeEntity.setPosition(0, 0, -2);
        scene.addEntity(cubeEntity);

        render.loadModels(modelDataList);
    }

    @Override
    public void input(Window window, Scene scene, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void update(Window window, Scene scene, long diffTimeMillis) {
        angle += 1.0f;
        if (angle >= 360) {
            angle = angle - 360;
        }
        cubeEntity.getRotation().identity().rotateAxis((float) Math.toRadians(angle), rotatingAngle);
        cubeEntity.updateModelMatrix();
    }
}
