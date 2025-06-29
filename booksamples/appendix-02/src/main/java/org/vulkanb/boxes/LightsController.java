package org.vulkanb.boxes;

import org.joml.Vector3f;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.wnd.KeyboardInput;

import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class LightsController {

    private final Light directionalLight;
    private float lightAngle = 75.0f;

    public LightsController(EngCtx engCtx) {
        Scene scene = engCtx.scene();
        scene.getAmbientLightColor().set(1.0f, 1.0f, 1.0f);
        scene.setAmbientLightIntensity(0.1f);
        List<Light> lights = new ArrayList<>();
        directionalLight = new Light(new Vector3f(0.0f, 0.0f, 0.0f), true, 5.0f, new Vector3f(1.0f, 1.0f, 1.0f));
        lights.add(directionalLight);
        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);
        updateDirectionalLight();
    }

    public void input(EngCtx engCtx) {
        float angleInc = 0.0f;
        KeyboardInput ki = engCtx.window().getKeyboardInput();
        if (ki.keyPressed(GLFW_KEY_LEFT_CONTROL)) {
            angleInc -= 0.5f;
        } else if (ki.keyPressed(GLFW_KEY_RIGHT_CONTROL)) {
            angleInc += 0.5f;
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
        Vector3f lightDirection = directionalLight.getPosition();
        lightDirection.x = 0;
        lightDirection.y = -yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
    }
}
