package org.vulkanb;

import org.joml.Vector3f;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.Entity;

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
        float[] positions = new float[]{
                -0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, 0.5f,
                0.5f, -0.5f, 0.5f,
                0.5f, 0.5f, 0.5f,
                -0.5f, 0.5f, -0.5f,
                0.5f, 0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f,
                0.5f, -0.5f, -0.5f,
        };
        float[] textCoords = new float[]{
                0.0f, 0.0f,
                0.5f, 0.0f,
                1.0f, 0.0f,
                1.0f, 0.5f,
                1.0f, 1.0f,
                0.5f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.5f,
        };
        int[] indices = new int[]{
                // Front face
                0, 1, 3, 3, 1, 2,
                // Top Face
                4, 0, 3, 5, 4, 3,
                // Right face
                3, 2, 7, 5, 3, 7,
                // Left face
                6, 1, 0, 6, 0, 4,
                // Bottom face
                2, 1, 6, 2, 6, 7,
                // Back face
                7, 6, 4, 7, 4, 5,
        };

        var modelId = "CubeModel";
        MeshData meshData = new MeshData("cube-mesh", positions, textCoords, indices);
        List<MeshData> meshDataList = new ArrayList<>();
        meshDataList.add(meshData);
        ModelData modelData = new ModelData(modelId, meshDataList);
        List<ModelData> models = new ArrayList<>();
        models.add(modelData);

        cubeEntity = new Entity("CubeEntity", modelId, new Vector3f(0.0f, 0.0f, -2.0f));
        engCtx.scene().addEntity(cubeEntity);

        return new InitData(models);
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
