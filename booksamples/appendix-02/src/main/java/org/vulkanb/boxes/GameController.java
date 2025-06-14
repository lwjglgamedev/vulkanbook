package org.vulkanb.boxes;

import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.sound.*;

import java.util.*;

public class GameController implements IGameLogic {

    private final AnimationController animationController;
    private final GameContext gameContext;
    private FontsManager fontsManager;
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
    public InitData init(EngCtx engCtx) {
        fontsManager = new FontsManager();
        List<ModelData> models = new ArrayList<>();

        ModelData playerModel = ModelLoader.loadModel("resources/models/player/player-model.json");
        models.add(playerModel);
        animationController.addModel(playerModel);
        ModelData floorModel = ModelLoader.loadModel("resources/models/floor/floor-model.json");
        models.add(floorModel);
        ModelData wallModel = ModelLoader.loadModel("resources/models/wall/wall-model.json");
        models.add(wallModel);
        ModelData boxModel = ModelLoader.loadModel("resources/models/box/box-model.json");
        models.add(boxModel);

        List<MaterialData> materials = new ArrayList<>();
        materials.addAll(ModelLoader.loadMaterials("resources/models/player/player-model_mat.json"));
        materials.addAll(ModelLoader.loadMaterials("resources/models/floor/floor_mat.json"));
        materials.addAll(ModelLoader.loadMaterials("resources/models/wall/wall_mat.json"));
        materials.addAll(ModelLoader.loadMaterials("resources/models/box/box_mat.json"));

        gameState = new MainMenuGameState(engCtx, gameContext);

        SoundManager soundManager = gameContext.getSoundManager();
        SoundSource soundSource = new SoundSource(false, true);
        soundSource.setGain(GameProperties.getInstance().getSoundGain());
        soundManager.addSoundSource(GameUtils.DEFAULT_SOUND_SOURCE, soundSource);
        soundManager.addSoundBuffer(GameUtils.SOUNDS_SELECT, new SoundBuffer("resources/sounds/confirmation_001.ogg"));
        soundManager.addSoundBuffer(GameUtils.SOUNDS_FOOT_STEP, new SoundBuffer("resources/sounds/footstep04.ogg"));
        soundManager.addSoundBuffer(GameUtils.SOUNDS_BOX_FINISH, new SoundBuffer("resources/sounds/switch_003.ogg"));

        return new InitData(models, materials, null);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        IGuiInstance guiInstance = gameContext.getGuiInstance();
        if (guiInstance != null) {
            guiInstance.defaultGuiInput(engCtx);
            guiInstance.processGui(engCtx, fontsManager);
        }
        gameState = gameState.processInput(engCtx, gameContext, diffTimeMillis);
        if (gameState == null) {
            engCtx.window().setShouldClose();
        }
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        animationController.update(engCtx);
        if (gameState != null) {
            gameState.update(engCtx, gameContext, diffTimeMillis);
        }
    }
}
