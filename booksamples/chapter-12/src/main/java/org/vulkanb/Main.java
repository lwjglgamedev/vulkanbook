package org.vulkanb;

import org.apache.logging.log4j.*;
import org.joml.*;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class Main implements IAppLogic {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 10.0f / 1E9f;

    private float angleInc;
    private Light directionalLight;
    private float lightAngle;

    public static void main(String[] args) {
        LOGGER.info("Starting application");

        Engine engine = new Engine("Vulkanbook", new Main());
        engine.start();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public void handleInput(Window window, Scene scene, long diffTimeMilisec) {
        float move = diffTimeMilisec * MOVEMENT_SPEED;
        Camera camera = scene.getCamera();
        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        } else if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        } else if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }
        if (window.isKeyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move);
        } else if (window.isKeyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move);
        }
        if (window.isKeyPressed(GLFW_KEY_LEFT)) {
            angleInc -= 0.05f;
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f;
        } else {
            angleInc = 0;
        }

        MouseInput mouseInput = window.getMouseInput();
        if (mouseInput.isRightButtonPressed()) {
            Vector2f displVec = mouseInput.getDisplVec();
            camera.addRotation((float) Math.toRadians(-displVec.x * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-displVec.y * MOUSE_SENSITIVITY));
        }

        lightAngle += angleInc;
        if (lightAngle < 0) {
            lightAngle = 0;
        } else if (lightAngle > 180) {
            lightAngle = 180;
        }
        float zValue = (float) Math.cos(Math.toRadians(lightAngle));
        float yValue = (float) Math.sin(Math.toRadians(lightAngle));
        Vector4f lightDirection = directionalLight.getPosition();
        lightDirection.x = 0;
        lightDirection.y = yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
        lightDirection.w = 0.0f;
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        String meshId = "SponzaMesh";
        MeshData[] meshDataList = ModelLoader.loadMeshes(meshId, "resources/models/sponza/Sponza.gltf",
                "resources/models/sponza");
        render.loadMeshes(meshDataList);

        Entity sponzaEntity = new Entity("SponzaEntity", meshId, new Vector3f(0.0f, 0.0f, 0.0f));
        scene.addEntity(sponzaEntity);

        Camera camera = scene.getCamera();
        camera.setPosition(0.0f, 5.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        List<Light> lights = new ArrayList<>();
        directionalLight = new Light();
        directionalLight.getPosition().set(0.0f, 1.0f, 0.0f, 0.0f);
        directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(directionalLight);

        Light light = new Light();
        light.getPosition().set(0, 1, 0, 1.0f);
        light.getColor().set(0.0f, 1.0f, 0.0f, 1.0f);
        lights.add(light);

        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);
    }
}
