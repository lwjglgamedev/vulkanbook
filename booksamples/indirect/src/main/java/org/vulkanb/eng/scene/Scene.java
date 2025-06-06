package org.vulkanb.eng.scene;

import org.joml.Vector3f;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.wnd.Window;

import java.util.*;

public class Scene {

    public static final int MAX_LIGHTS = 10;
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;

    private final Vector3f ambientLight;
    private final Camera camera;
    private final Map<String, List<Entity>> entitiesMap;
    private final Projection projection;
    private Light[] lights;

    public Scene(Window window) {
        entitiesMap = new HashMap<>();
        var engCfg = EngCfg.getInstance();
        projection = new Projection(engCfg.getFov(), engCfg.getZNear(), engCfg.getZFar(), window.getWidth(),
                window.getHeight());
        camera = new Camera();
        ambientLight = new Vector3f();
    }

    public void addEntity(Entity entity) {
        List<Entity> entities = entitiesMap.computeIfAbsent(entity.getModelId(), k -> new ArrayList<>());
        entities.add(entity);
    }

    public Vector3f getAmbientLight() {
        return ambientLight;
    }

    public Camera getCamera() {
        return camera;
    }

    public List<Entity> getEntitiesByModelId(String modelId) {
        return entitiesMap.get(modelId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return entitiesMap;
    }

    public Light[] getLights() {
        return this.lights;
    }

    public Projection getProjection() {
        return projection;
    }

    public void removeAllEntities() {
        entitiesMap.clear();
    }

    public void removeEntity(Entity entity) {
        List<Entity> entities = entitiesMap.get(entity.getModelId());
        if (entities != null) {
            entities.removeIf(e -> e.getId().equals(entity.getId()));
        }
    }

    public void setLights(Light[] lights) {
        int numLights = lights != null ? lights.length : 0;
        if (numLights > MAX_LIGHTS) {
            throw new RuntimeException("Maximum number of lights set to: " + MAX_LIGHTS);
        }
        this.lights = lights;
    }
}
