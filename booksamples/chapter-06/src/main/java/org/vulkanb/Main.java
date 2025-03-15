package org.vulkanb;

import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;

import java.util.*;

public class Main implements IGameLogic {

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
        var modelId = "TriangleModel";
        MeshData meshData = new MeshData("triangle-mesh", new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f},
                new int[]{0, 1, 2});
        List<MeshData> meshDataList = new ArrayList<>();
        meshDataList.add(meshData);
        ModelData modelData = new ModelData(modelId, meshDataList);
        List<ModelData> models = new ArrayList<>();
        models.add(modelData);

        return new InitData(models);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }
}
