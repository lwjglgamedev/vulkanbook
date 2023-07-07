package org.vulkanb.boxes;

import org.joml.Vector4f;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.*;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class LightsController {

    private final Light directionalLight;
    private float lightAngle = 35.0f;

    public LightsController(Scene scene) {
        scene.getAmbientLight().set(0.4f, 0.4f, 0.4f, 1.0f);
        List<Light> lights = new ArrayList<>();
        directionalLight = new Light();
        directionalLight.getColor().set(1.0f, 1.0f, 0.5f, 1.0f);
        lights.add(directionalLight);
        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);
        updateDirectionalLight();
    }

    public void input(Window window, Scene scene) {
        float angleInc = 0.0f;
        if (window.isKeyPressed(GLFW_KEY_LEFT_CONTROL)) {
            angleInc -= 0.5f;
            scene.setLightChanged(true);
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT_CONTROL)) {
            angleInc += 0.5f;
            scene.setLightChanged(true);
        } else {
            scene.setLightChanged(false);
        }
        lightAngle += angleInc;
        if (lightAngle < 0) {
            lightAngle = 0;
        } else if (lightAngle > 180) {
            lightAngle = 180;
        }
        updateDirectionalLight();
    }

    private void updateDirectionalLight() {
        float zValue = (float) Math.cos(Math.toRadians(lightAngle));
        float yValue = (float) Math.sin(Math.toRadians(lightAngle));
        Vector4f lightDirection = directionalLight.getPosition();
        lightDirection.x = 0;
        lightDirection.y = yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
        lightDirection.w = 0.0f;
    }
}
