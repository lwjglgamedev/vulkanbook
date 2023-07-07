package org.vulkanb.boxes.mainmenu;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.runlevel.RunLevelGameState;
import org.vulkanb.boxes.selectlevel.SelectLevelGameState;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.*;

public class MainMenuGameState implements IGameState {

    private final MainMenuGui startGui;

    public MainMenuGameState(Window window, Scene scene, GameContext gameContext) {
        startGui = new MainMenuGui(window);
        new LightsController(scene);
        scene.setGuiInstance(startGui);
        scene.removeAllEntities();
        gameContext.getLevelsLoader().loadGameLevel(0, scene);

        Camera camera = scene.getCamera();
        camera.setPosition(8.0f, 8.0f, 12.0f);
        camera.setRotation((float) Math.toRadians(30.0f), 0.0f);
    }

    @Override
    public IGameState processInput(Window window, Scene scene, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        switch (startGui.getSelectedOption()) {
            case START_GAME -> {
                gameContext.getSoundManager().play(GameUtils.DEFAULT_SOUND_SOURCE, GameUtils.SOUNDS_SELECT);
                nextState = new RunLevelGameState(window, scene, gameContext, 0);
            }
            case LOAD_GAME -> nextState = new SelectLevelGameState(window, scene, gameContext);
            case EXIT_GAME -> nextState = null;
            default -> {
                // Do nothing
            }
        }

        return nextState;
    }

    @Override
    public void update(Scene scene, GameContext gameContext, long diffTimeMillis) {
        // Nothing to be done here
    }
}
