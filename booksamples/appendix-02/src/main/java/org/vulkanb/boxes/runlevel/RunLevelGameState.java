package org.vulkanb.boxes.runlevel;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.scene.*;

import java.util.List;

public class RunLevelGameState implements IGameState {

    private final CameraController cameraController;
    private final LightsController lightsController;
    private final NextLevelGui nextGui;
    private final PlayerController playerController;
    private final RunLevelGui runLevelGui;

    private boolean showDialog = false;

    public RunLevelGameState(EngCtx engCtx, GameContext gameContext, int level) {
        Scene scene = engCtx.scene();
        scene.removeAllEntities();

        GameLevel gameLevel = gameContext.getLevelsLoader().loadGameLevel(level, scene);
        List<MovableItem> movableItems = gameLevel.getMovableItems();
        gameContext.selectLevel(gameLevel);
        MovableItem player = null;
        for (MovableItem movableItem : movableItems) {
            if (movableItem.getMovableItemType() == MovableItem.MovableItemType.PLAYER) {
                player = movableItem;
            } else {
                gameContext.addBox(movableItem);
            }
        }

        Camera camera = scene.getCamera();
        camera.setPosition(8.0f, 8.0f, 12.0f);
        camera.setRotation((float) Math.toRadians(30.0f), 0.0f);

        lightsController = new LightsController(engCtx);
        cameraController = new CameraController();
        playerController = new PlayerController(player);
        nextGui = new NextLevelGui();
        runLevelGui = new RunLevelGui(gameLevel);
        gameContext.setGuiInstance(runLevelGui);
    }

    @Override
    public IGameState processInput(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        if (!showDialog) {
            playerController.input(engCtx, gameContext);
            cameraController.input(engCtx, diffTimeMillis);
            lightsController.input(engCtx);

            if (runLevelGui.isBackPressed()) {
                nextState = new MainMenuGameState(engCtx, gameContext);
            } else {
                showDialog = !gameContext.hasBoxes();
                if (showDialog) {
                    gameContext.setGuiInstance(nextGui);
                }
            }
        } else {
            if (nextGui.isNextLevel()) {
                nextState = new RunLevelGameState(engCtx, gameContext, gameContext.nextLevel());
            } else if (nextGui.isDontContinue()) {
                nextState = new MainMenuGameState(engCtx, gameContext);
            }
        }

        return nextState;
    }

    @Override
    public void update(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        playerController.update(diffTimeMillis);
        gameContext.updateBoxes(engCtx, diffTimeMillis);
    }
}
