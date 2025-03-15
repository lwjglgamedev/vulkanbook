package org.vulkanb.eng.scene;

import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.wnd.Window;

import java.util.*;

public class Scene {

    private final Camera camera;
    private final List<Entity> entities;
    private final Projection projection;

    public Scene(Window window) {
        entities = new ArrayList<>();
        var engCfg = EngCfg.getInstance();
        projection = new Projection(engCfg.getFov(), engCfg.getZNear(), engCfg.getZFar(), window.getWidth(),
                window.getHeight());
        camera = new Camera();
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public Camera getCamera() {
        return camera;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public Projection getProjection() {
        return projection;
    }

    public void removeAllEntities() {
        entities.clear();
    }

    public void removeEntity(Entity entity) {
        entities.removeIf(entity1 -> entity1.getId().equals(entity.getId()));
    }
}
