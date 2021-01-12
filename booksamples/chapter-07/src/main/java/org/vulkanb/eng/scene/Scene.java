package org.vulkanb.eng.scene;

import org.vulkanb.eng.Window;

import java.util.*;

public class Scene {

    private Map<String, List<Entity>> entitiesMap;
    private Projection projection;

    public Scene(Window window) {
        entitiesMap = new HashMap<>();
        projection = new Projection();
        projection.resize(window.getWidth(), window.getHeight());
    }

    public void addEntity(Entity entity) {
        List<Entity> entities = entitiesMap.get(entity.getModelId());
        if (entities == null) {
            entities = new ArrayList<>();
            entitiesMap.put(entity.getModelId(), entities);
        }
        entities.add(entity);
    }

    public List<Entity> getEntitiesByModelId(String modelId) {
        return entitiesMap.get(modelId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return entitiesMap;
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
}
