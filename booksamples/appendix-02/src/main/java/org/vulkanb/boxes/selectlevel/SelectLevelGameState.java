package org.vulkanb.boxes.selectlevel;

import org.vulkanb.boxes.*;
import org.vulkanb.boxes.mainmenu.MainMenuGameState;
import org.vulkanb.boxes.runlevel.RunLevelGameState;
import org.vulkanb.eng.EngCtx;

public class SelectLevelGameState implements IGameState {

    private final SelectLevelGui selectLevelGui;

    public SelectLevelGameState(GameContext gameContext) {
        selectLevelGui = new SelectLevelGui(gameContext.getLevelsLoader().getLevelDataList());
        gameContext.setGuiInstance(selectLevelGui);
    }

    @Override
    public IGameState processInput(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        IGameState nextState = this;
        int levelSelected = selectLevelGui.getItemSelected();
        if (levelSelected >= 0) {
            engCtx.scene().removeAllEntities();
            nextState = new RunLevelGameState(engCtx, gameContext, levelSelected);
        } else if (selectLevelGui.isBackSelected()) {
            nextState = new MainMenuGameState(engCtx, gameContext);
        }
        return nextState;
    }

    @Override
    public void update(EngCtx engCtx, GameContext gameContext, long diffTimeMillis) {
        // Nothing to be done here
    }
}
