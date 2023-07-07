package org.vulkanb.boxes.selectlevel;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.boxes.runlevel.RunLevelGameState;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.Scene;

public class SelectLevelGameState implements IGameState {

    private final SelectLevelGui selectLevelGui;

    public SelectLevelGameState(Window window, Scene scene, GameContext gameContext) {
        selectLevelGui = new SelectLevelGui(window, gameContext.getLevelsLoader().getLevelDataList());
        scene.setGuiInstance(selectLevelGui);
    }

    @Override
    public IGameState processInput(Window window, Scene scene, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        int levelSelected = selectLevelGui.getItemSelected();
        if (levelSelected >= 0) {
            scene.removeAllEntities();
            nextState = new RunLevelGameState(window, scene, gameContext, levelSelected);
        } else if (selectLevelGui.isBackSelected()) {
            nextState = new MainMenuGameState(window, scene, gameContext);
        }
        return nextState;
    }

    @Override
    public void update(Scene scene, GameContext gameContext, long diffTimeMillis) {
        // Nothing to be done here
    }
}
