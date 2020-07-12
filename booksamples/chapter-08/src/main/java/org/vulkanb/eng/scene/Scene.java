package org.vulkanb.eng.scene;

import org.vulkanb.eng.Window;

import java.util.*;

public class Scene {

    private Map<String, List<Entity>> entitiesMap;
    private Perspective perspective;

    public Scene(Window window) {
        entitiesMap = new HashMap<>();
        perspective = new Perspective();
        perspective.resize(window.getWidth(), window.getHeight());
    }

    public void addEntity(Entity entity) {
        List<Entity> entities = entitiesMap.get(entity.getMeshId());
        if (entities == null) {
            entities = new ArrayList<>();
            entitiesMap.put(entity.getMeshId(), entities);
        }
        entities.add(entity);
    }

    public List<Entity> getEntitiesByMeshId(String meshId) {
        return entitiesMap.get(meshId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return entitiesMap;
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
}
