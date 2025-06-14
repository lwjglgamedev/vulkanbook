package org.vulkanb.boxes.mainmenu;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.runlevel.RunLevelGameState;
import org.vulkanb.boxes.selectlevel.SelectLevelGameState;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.scene.*;

public class MainMenuGameState implements IGameState {

    private final LightsController lightsController;
    private final MainMenuGui startGui;

    public MainMenuGameState(EngCtx engCtx, GameContext gameContext) {
        Scene scene = engCtx.scene();

        startGui = new MainMenuGui();
        lightsController = new LightsController(engCtx);
        gameContext.setGuiInstance(startGui);
        scene.removeAllEntities();
        gameContext.getLevelsLoader().loadGameLevel(0, scene);

        Camera camera = scene.getCamera();
        camera.setPosition(8.0f, 8.0f, 12.0f);
        camera.setRotation((float) Math.toRadians(30.0f), 0.0f);
    }

    @Override
    public IGameState processInput(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        switch (startGui.getSelectedOption()) {
            case START_GAME -> {
                gameContext.getSoundManager().play(GameUtils.DEFAULT_SOUND_SOURCE, GameUtils.SOUNDS_SELECT);
                nextState = new RunLevelGameState(engCtx, gameContext, 0);
            }
            case LOAD_GAME -> nextState = new SelectLevelGameState(gameContext);
            case EXIT_GAME -> nextState = null;
            default -> {
                // Do nothing
            }
        }

        return nextState;
    }

    @Override
    public void update(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        // Nothing to be done here
    }
}
