package org.vulkanb;

import org.joml.*;
import org.lwjgl.openal.AL11;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.sound.*;
import org.vulkanb.eng.wnd.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class Main implements IGameLogic {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 0.01f;
    private static final String SOUND_BUFFER_MUSIC = "music-sound-buffer";
    private static final String SOUND_BUFFER_PLAYER = "player-sound-buffer";
    private static final String SOUND_SOURCE_MUSIC = "music-sound-source";
    private static final String SOUND_SOURCE_PLAYER = "player-sound-source";

    private long soundTimer = 0;

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

        ModelData sponzaModel = ModelLoader.loadModel("resources/models/sponza/Sponza.json");
        models.add(sponzaModel);
        Entity sponzaEntity = new Entity("SponzaEntity", sponzaModel.id(), new Vector3f(0.0f, 0.0f, 0.0f));
        scene.addEntity(sponzaEntity);

        List<MaterialData> materials = new ArrayList<>(ModelLoader.loadMaterials("resources/models/sponza/Sponza_mat.json"));

        Camera camera = scene.getCamera();
        camera.setPosition(0.0f, 5.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        initSounds(engCtx);

        return new InitData(models, materials, null);
    }

    private void initSounds(EngCtx engCtx) {
        SoundManager soundMgr = engCtx.soundManager();
        Camera camera = engCtx.scene().getCamera();
        soundMgr.setAttenuationModel(AL11.AL_EXPONENT_DISTANCE);
        soundMgr.setListener(new SoundListener(camera.getPosition()));

        SoundBuffer buffer = new SoundBuffer("resources/sounds/creak1.ogg");
        soundMgr.addSoundBuffer(SOUND_BUFFER_PLAYER, buffer);
        SoundSource playerSoundSource = new SoundSource(false, false);
        playerSoundSource.setPosition(new Vector3f(0.0f, 0.0f, 0.0f));
        playerSoundSource.setBuffer(buffer.getBufferId());
        soundMgr.addSoundSource(SOUND_SOURCE_PLAYER, playerSoundSource);

        buffer = new SoundBuffer("resources/sounds/woo_scary.ogg");
        soundMgr.addSoundBuffer(SOUND_BUFFER_MUSIC, buffer);
        SoundSource source = new SoundSource(true, true);
        source.setBuffer(buffer.getBufferId());
        soundMgr.addSoundSource(SOUND_SOURCE_MUSIC, source);
        source.play();
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

        MouseInput mi = window.getMouseInput();
        if (mi.isRightButtonPressed()) {
            Vector2f deltaPos = mi.getDeltaPos();
            camera.addRotation((float) Math.toRadians(-deltaPos.y * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-deltaPos.x * MOUSE_SENSITIVITY));
        }

        engCtx.soundManager().updateListenerPosition(camera);
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        soundTimer += diffTimeMillis;
        if (soundTimer > 5000) {
            engCtx.soundManager().play(SOUND_SOURCE_PLAYER, SOUND_BUFFER_PLAYER);
            soundTimer -= 5000;
        }
    }
}
