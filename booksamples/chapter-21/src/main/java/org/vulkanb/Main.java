package org.vulkanb;

import org.joml.*;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.wnd.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class Main implements IGameLogic {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 0.01f;

    private final Vector3f rotatingAxis = new Vector3f(1, 1, 1);
    private float angle = 0.0f;
    private float angleInc;
    private Entity cubeEntity1;
    private Entity cubeEntity2;
    private Light dirLight;
    private float lightAngle = 270;

    public static void main(String[] args) {
        Logger.info("Starting application");
        var engine = new Engine("Vulkan Book", new Main());
        Logger.info("Started application");
        engine.run();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    @Override
    public InitData init(EngCtx engCtx) {
        Scene scene = engCtx.scene();
        List<ModelData> models = new ArrayList<>();
        List<MaterialData> materials = new ArrayList<>();

        ModelData sponzaModel = ModelLoader.loadModel("resources/models/sponza/Sponza.json");
        models.add(sponzaModel);
        Entity sponzaEntity = new Entity("SponzaEntity", sponzaModel.id(), new Vector3f(0.0f, 0.0f, 0.0f));
        scene.addEntity(sponzaEntity);

        materials.addAll(ModelLoader.loadMaterials("resources/models/sponza/Sponza_mat.json"));

        ModelData modelData = ModelLoader.loadModel("resources/models/cube/cube.json");
        models.add(modelData);
        cubeEntity1 = new Entity("CubeEntity", modelData.id(), new Vector3f(0.0f, 2.0f, 0.0f));
        scene.addEntity(cubeEntity1);

        cubeEntity2 = new Entity("CubeEntity2", modelData.id(), new Vector3f(-2.0f, 2.0f, 0.0f));
        scene.addEntity(cubeEntity2);

        materials.addAll(ModelLoader.loadMaterials("resources/models/cube/cube_mat.json"));

        scene.getAmbientLight().set(0.8f, 0.8f, 0.8f);
        List<Light> lights = new ArrayList<>();
        dirLight = new Light(new Vector4f(0.0f, -1.0f, 0.0f, 0.0f), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
        lights.add(dirLight);

        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);

        Camera camera = scene.getCamera();
        camera.setPosition(-5.0f, 3.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        return new InitData(models, materials, null);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        Scene scene = engCtx.scene();
        Window window = engCtx.window();

        KeyboardInput ki = window.getKeyboardInput();
        float move = diffTimeMillis * MOVEMENT_SPEED;
        Camera camera = scene.getCamera();
        if (ki.keyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        } else if (ki.keyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (ki.keyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        } else if (ki.keyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }
        if (ki.keyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move);
        } else if (ki.keyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move);
        }

        if (ki.keyPressed(GLFW_KEY_LEFT)) {
            angleInc -= 0.05f;
        } else if (ki.keyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f;
        } else {
            angleInc = 0;
        }

        MouseInput mi = window.getMouseInput();
        if (mi.isRightButtonPressed()) {
            Vector2f deltaPos = mi.getDeltaPos();
            camera.addRotation((float) Math.toRadians(-deltaPos.y * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-deltaPos.x * MOUSE_SENSITIVITY));
        }

        if (angleInc != 0.0) {
            lightAngle += angleInc;
            if (lightAngle < 180) {
                lightAngle = 180;
            } else if (lightAngle > 360) {
                lightAngle = 360;
            }
            updateDirLight();
        }
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        angle += 1.0f;
        if (angle >= 360) {
            angle = angle - 360;
        }
        float angleRad = (float) Math.toRadians(angle);
        cubeEntity1.getRotation().identity().rotateAxis(angleRad, rotatingAxis);
        cubeEntity1.updateModelMatrix();
        cubeEntity2.getRotation().identity().rotateAxis(-angleRad, rotatingAxis);
        cubeEntity2.updateModelMatrix();
    }

    private void updateDirLight() {
        float zValue = (float) Math.cos(Math.toRadians(lightAngle));
        float yValue = (float) Math.sin(Math.toRadians(lightAngle));
        Vector4f lightDirection = dirLight.position();
        lightDirection.x = 0;
        lightDirection.y = yValue;
        lightDirection.z = zValue;
        lightDirection.normalize();
        lightDirection.w = 0.0f;
    }
}
