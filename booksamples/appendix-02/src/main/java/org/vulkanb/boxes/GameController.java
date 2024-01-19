package org.vulkanb.boxes;

import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.eng.*;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.sound.*;

import java.util.*;

public class GameController implements IAppLogic {

    private final GameContext gameContext;
    private AnimationController animationController;
    private IGameState gameState;

    public GameController() {
        gameContext = new GameContext();
        animationController = new AnimationController();
    }

    @Override
    public void cleanup() {
        gameContext.cleanup();
    }

    @Override
    public void init(Window window, Scene scene, Render render) {
        List<ModelData> modelDataList = new ArrayList<>();

        ModelData playerModel = ModelLoader.loadModel(GameUtils.PLAYER_MODEl_ID, "resources/models/player/player.gltf",
                "resources/models/player", true);
        modelDataList.add(playerModel);
        animationController.addModel(playerModel);

        ModelData floorModel = ModelLoader.loadModel(GameUtils.FLOOR_MODEl_ID, "resources/models/floor/floor.obj",
                "resources/models/floor", false);
        modelDataList.add(floorModel);

        ModelData wallModel = ModelLoader.loadModel(GameUtils.WALL_MODEl_ID, "resources/models/wall/wall.gltf",
                "resources/models/wall", false);
        modelDataList.add(wallModel);

        ModelData boxModel = ModelLoader.loadModel(GameUtils.BOX_MODEl_ID, "resources/models/box/box.gltf",
                "resources/models/box", false);
        modelDataList.add(boxModel);
        render.loadModels(modelDataList);

        gameState = new MainMenuGameState(window, scene, gameContext);

        SoundManager soundManager = gameContext.getSoundManager();
        SoundSource soundSource = new SoundSource(false, true);
        soundSource.setGain(GameProperties.getInstance().getSoundGain());
        soundManager.addSoundSource(GameUtils.DEFAULT_SOUND_SOURCE, soundSource);
        soundManager.addSoundBuffer(GameUtils.SOUNDS_SELECT, new SoundBuffer("resources/sounds/confirmation_001.ogg"));
        soundManager.addSoundBuffer(GameUtils.SOUNDS_FOOT_STEP, new SoundBuffer("resources/sounds/footstep04.ogg"));
        soundManager.addSoundBuffer(GameUtils.SOUNDS_BOX_FINISH, new SoundBuffer("resources/sounds/switch_003.ogg"));
    }

    @Override
    public void input(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed) {
        gameState = gameState.processInput(window, scene, gameContext, diffTimeMillis);
        if (gameState == null) {
            window.setShouldClose();
        }
    }

    @Override
    public void update(Window window, Scene scene, long diffTimeMillis) {
        animationController.update(scene);
        if (gameState != null) {
            gameState.update(scene, gameContext, diffTimeMillis);
        }
    }
}
