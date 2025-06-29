package org.vulkanb.eng.scene;

import org.joml.Vector3f;
import org.vulkanb.eng.EngCfg;
import org.vulkanb.eng.wnd.Window;

import java.util.*;

public class Scene {

    public static final int MAX_LIGHTS = 10;
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;

    private final Vector3f ambientLightColor;
    private final Camera camera;
    private final List<Entity> entities;
    private final Projection projection;
    private float ambientLightIntensity;
    private Light[] lights;

    public Scene(Window window) {
        entities = new ArrayList<>();
        var engCfg = EngCfg.getInstance();
        projection = new Projection(engCfg.getFov(), engCfg.getZNear(), engCfg.getZFar(), window.getWidth(),
                window.getHeight());
        camera = new Camera();
        ambientLightColor = new Vector3f();
        ambientLightIntensity = 0.0f;
    }

    public void addEntity(Entity entity) {
        entities.add(entity);
    }

    public Vector3f getAmbientLightColor() {
        return ambientLightColor;
    }

    public float getAmbientLightIntensity() {
        return ambientLightIntensity;
    }

    public Camera getCamera() {
        return camera;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public Light[] getLights() {
        return lights;
    }

    public Projection getProjection() {
        return projection;
    }

    public void setAmbientLightIntensity(float ambientLightIntensity) {
        this.ambientLightIntensity = ambientLightIntensity;
    }

    public void setLights(Light[] lights) {
        int numLights = lights != null ? lights.length : 0;
        if (numLights > MAX_LIGHTS) {
            throw new RuntimeException("Maximum number of lights set to: " + MAX_LIGHTS);
        }
        this.lights = lights;
    }
}
