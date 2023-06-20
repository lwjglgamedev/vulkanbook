package org.vulkanb;

import org.joml.*;
import org.lwjgl.openal.AL11;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.sound.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class Main implements IAppLogic {
    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 0.01f;
    private static final String SOUND_BUFFER_PLAYER = "player-sound-buffer";
    private static final String SOUND_SOURCE_PLAYER = "player-sound-source";

    private float angleInc;
    private Entity bobEntity;
    private Light directionalLight;
    private float lightAngle = 90.1f;
    private int maxFrames = 0;
    private SoundManager soundMgr;

    public static void main(String[] args) {
        Logger.info("Starting application");
        Engine engine = new Engine("Vulkan Book", new Main());
        engine.start();
    }

    @Override
    public void cleanup() {
        soundMgr.cleanup();
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        List<ModelData> modelDataList = new ArrayList<>();

        String sponzaModelId = "sponza-model";
        ModelData sponzaModelData = ModelLoader.loadModel(sponzaModelId, "resources/models/sponza/Sponza.gltf",
                "resources/models/sponza", false);
        modelDataList.add(sponzaModelData);
        Entity sponzaEntity = new Entity("SponzaEntity", sponzaModelId, new Vector3f(0.0f, 0.0f, 0.0f));
        scene.addEntity(sponzaEntity);

        String bobModelId = "bob-model";
        ModelData bobModelData = ModelLoader.loadModel(bobModelId, "resources/models/bob/boblamp.md5mesh",
                "resources/models/bob", true);
        maxFrames = bobModelData.getAnimationsList().get(0).frames().size();
        modelDataList.add(bobModelData);
        bobEntity = new Entity("BobEntity", bobModelId, new Vector3f(0.0f, 0.0f, 0.0f));
        bobEntity.setScale(0.04f);
        bobEntity.getRotation().rotateY((float) Math.toRadians(-90.0f));
        bobEntity.updateModelMatrix();
        bobEntity.setEntityAnimation(new Entity.EntityAnimation(true, 0, 0));
        scene.addEntity(bobEntity);

        render.loadModels(modelDataList);

        Camera camera = scene.getCamera();
        camera.setPosition(-6.0f, 2.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        scene.getAmbientLight().set(0.2f, 0.2f, 0.2f, 1.0f);
        List<Light> lights = new ArrayList<>();
        directionalLight = new Light();
        directionalLight.getColor().set(1.0f, 1.0f, 1.0f, 1.0f);
        lights.add(directionalLight);
        updateDirectionalLight();

        Light[] lightArr = new Light[lights.size()];
        lightArr = lights.toArray(lightArr);
        scene.setLights(lightArr);

        initSounds(bobEntity.getPosition(), camera);
    }

    private void initSounds(Vector3f position, Camera camera) {
        soundMgr = new SoundManager();
        soundMgr.setAttenuationModel(AL11.AL_EXPONENT_DISTANCE);
        soundMgr.setListener(new SoundListener(camera.getPosition()));

        SoundBuffer buffer = new SoundBuffer("resources/sounds/creak1.ogg");
        soundMgr.addSoundBuffer(SOUND_BUFFER_PLAYER, buffer);
        SoundSource playerSoundSource = new SoundSource(false, false);
        playerSoundSource.setPosition(position);
        playerSoundSource.setBuffer(buffer.getBufferId());
        soundMgr.addSoundSource(SOUND_SOURCE_PLAYER, playerSoundSource);

        buffer = new SoundBuffer("resources/sounds/woo_scary.ogg");
        soundMgr.addSoundBuffer("MUSIC", buffer);
        SoundSource source = new SoundSource(true, true);
        source.setBuffer(buffer.getBufferId());
        soundMgr.addSoundSource("MUSIC", source);
        source.play();
    }

    @Override
    public void input(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed) {
        if (inputConsumed) {
            return;
        }
        float move = diffTimeMillis * MOVEMENT_SPEED;
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
            scene.setLightChanged(true);
        } else if (window.isKeyPressed(GLFW_KEY_RIGHT)) {
            angleInc += 0.05f;
            scene.setLightChanged(true);
        } else {
            angleInc = 0;
            scene.setLightChanged(false);
        }

        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            bobEntity.getEntityAnimation().setStarted(!bobEntity.getEntityAnimation().isStarted());
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
        updateDirectionalLight();

        soundMgr.updateListenerPosition(camera);
    }

    @Override
    public void update(Window window, Scene scene, long diffTimeMillis) {
        Entity.EntityAnimation entityAnimation = bobEntity.getEntityAnimation();
        if (entityAnimation != null && entityAnimation.isStarted()) {
            int currentFrame = Math.floorMod(entityAnimation.getCurrentFrame() + 1, maxFrames);
            entityAnimation.setCurrentFrame(currentFrame);
            if (currentFrame == 45) {
                soundMgr.play(SOUND_SOURCE_PLAYER, SOUND_BUFFER_PLAYER);
            }
        }
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
