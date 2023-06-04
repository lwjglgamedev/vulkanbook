package org.vulkanb;

import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;

import java.util.*;

public class Main implements IAppLogic {

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
        String modelId = "TriangleModel";
        ModelData.MeshData meshData = new ModelData.MeshData(new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f},
                new int[]{0, 1, 2});
        List<ModelData.MeshData> meshDataList = new ArrayList<>();
        meshDataList.add(meshData);
        ModelData modelData = new ModelData(modelId, meshDataList);
        List<ModelData> modelDataList = new ArrayList<>();
        modelDataList.add(modelData);
        render.loadModels(modelDataList);
    }

    @Override
    public void input(Window window, Scene scene, long diffTimeMillis) {
        // To be implemented
    }

    @Override
    public void update(Window window, Scene scene, long diffTimeMillis) {
        // To be implemented
    }
}
