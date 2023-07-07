package org.vulkanb.boxes.runlevel;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.*;

import java.util.List;

public class RunLevelGameState implements IGameState {

    private final CameraController cameraController;
    private final LightsController lightsController;
    private final NextLevelGui nextGui;
    private final PlayerController playerController;
    private final RunLevelGui runLevelGui;

    private boolean showDialog = false;

    public RunLevelGameState(Window window, Scene scene, GameContext gameContext, int level) {
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

        lightsController = new LightsController(scene);
        cameraController = new CameraController();
        playerController = new PlayerController(player);
        nextGui = new NextLevelGui(window);
        runLevelGui = new RunLevelGui(window, gameLevel);
        scene.setGuiInstance(runLevelGui);
    }

    @Override
    public IGameState processInput(Window window, Scene scene, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        if (!showDialog) {
            playerController.input(window, gameContext);
            cameraController.input(window, scene, diffTimeMillis);
            lightsController.input(window, scene);

            if (runLevelGui.isBackPressed()) {
                nextState = new MainMenuGameState(window, scene, gameContext);
            } else {
                showDialog = !gameContext.hasBoxes();
                if (showDialog) {
                    scene.setGuiInstance(nextGui);
                }
            }
        } else {
            if (nextGui.isNextLevel()) {
                nextState = new RunLevelGameState(window, scene, gameContext, gameContext.nextLevel());
            } else if (nextGui.isDontContinue()) {
                nextState = new MainMenuGameState(window, scene, gameContext);
            }
        }

        return nextState;
    }

    @Override
    public void update(Scene scene, GameContext gameContext, long diffTimeMillis) {
        playerController.update(diffTimeMillis);
        gameContext.updateBoxes(scene, diffTimeMillis);
    }
}
