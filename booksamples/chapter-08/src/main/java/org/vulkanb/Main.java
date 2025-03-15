package org.vulkanb;

import org.joml.Vector3f;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Main implements IGameLogic {

    private final Vector3f rotatingAngle = new Vector3f(1, 1, 1);
    private float angle = 0;
    private Entity cubeEntity;

    public static void main(String[] args) {
        Logger.info("Starting application");
        var engine = new Engine("Vulkan Book", new Main());
        Logger.info("Started application");
        engine.run();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public InitData init(EngCtx engCtx) {
        Scene scene = engCtx.scene();
        List<ModelData> models = new ArrayList<>();

        ModelData cubeModel = ModelLoader.loadModel("resources/models/cube/cube.json");
        models.add(cubeModel);
        cubeEntity = new Entity("CubeEntity", cubeModel.id(), new Vector3f(0.0f, 0.0f, -2.0f));
        scene.addEntity(cubeEntity);

        List<MaterialData> materials = new ArrayList<>(ModelLoader.loadMaterials("resources/models/cube/cube_mat.json"));

        return new InitData(models, materials);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        angle += 1.0f;
        if (angle >= 360) {
            angle = angle - 360;
        }
        cubeEntity.getRotation().identity().rotateAxis((float) Math.toRadians(angle), rotatingAngle);
        cubeEntity.updateModelMatrix();
    }
}
