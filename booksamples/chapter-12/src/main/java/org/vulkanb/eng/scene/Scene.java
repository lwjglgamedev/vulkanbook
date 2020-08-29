package org.vulkanb.eng.scene;

import org.joml.Vector4f;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.graph.vk.GraphConstants;

import java.util.*;

public class Scene {

    private Vector4f ambientLight;
    private Camera camera;
    private Map<String, List<Entity>> entitiesMap;
    private Light[] lights;
    private Perspective perspective;

    public Scene(Window window) {
        entitiesMap = new HashMap<>();
        perspective = new Perspective();
        perspective.resize(window.getWidth(), window.getHeight());
        camera = new Camera();
        ambientLight = new Vector4f();
    }

    public void addEntity(Entity entity) {
        List<Entity> entities = entitiesMap.get(entity.getMeshId());
        if (entities == null) {
            entities = new ArrayList<>();
            entitiesMap.put(entity.getMeshId(), entities);
        }
        entities.add(entity);
    }

    public Vector4f getAmbientLight() {
        return ambientLight;
    }

    public Camera getCamera() {
        return camera;
    }

    public List<Entity> getEntitiesByMeshId(String meshId) {
        return entitiesMap.get(meshId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return entitiesMap;
    }

    public Light[] getLights() {
        return this.lights;
    }

    public Perspective getPerspective() {
        return perspective;
    }

    public void removeAllEntities() {
        entitiesMap.clear();
    }

    public void removeEntity(Entity entity) {
        List<Entity> entities = entitiesMap.get(entity.getMeshId());
        if (entities != null) {
            entities.removeIf(e -> e.getId().equals(entity.getId()));
        }
    }

    public void setLights(Light[] lights) {
        int numLights = lights != null ? lights.length : 0;
        if (numLights > GraphConstants.MAX_LIGHTS) {
            throw new RuntimeException("Maximum number of lights set to: " + GraphConstants.MAX_LIGHTS);
        }
        this.lights = lights;
    }
}
