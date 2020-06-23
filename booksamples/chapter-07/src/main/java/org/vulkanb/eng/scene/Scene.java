package org.vulkanb.eng.scene;

import org.vulkanb.eng.Window;

import java.util.*;

// TODO: Resize perspective
public class Scene {

    private Map<String, List<Entity>> entitiesMap;
    private Perspective perspective;

    public Scene(Window window) {
        this.entitiesMap = new HashMap<>();
        this.perspective = new Perspective();
        this.perspective.resize(window.getWidth(), window.getHeight());
    }

    public void addEntity(Entity entity) {
        List<Entity> entities = this.entitiesMap.get(entity.getMeshId());
        if (entities == null) {
            entities = new ArrayList<>();
            this.entitiesMap.put(entity.getMeshId(), entities);
        }
        entities.add(entity);
    }

    public List<Entity> getEntitiesByMeshId(String meshId) {
        return this.entitiesMap.get(meshId);
    }

    public Map<String, List<Entity>> getEntitiesMap() {
        return this.entitiesMap;
    }

    public Perspective getPerspective() {
        return perspective;
    }

    public void removeAllEntities() {
        this.entitiesMap.clear();
    }

    public void removeEntity(Entity entity) {
        List<Entity> entities = this.entitiesMap.get(entity.getMeshId());
        if (entities != null) {
            entities.removeIf(e -> e.getId().equals(entity.getId()));
        }
    }
}
