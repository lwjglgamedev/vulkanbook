package org.vulkanb.boxes;

import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.sound.SoundManager;

import java.util.*;

import static org.vulkanb.boxes.runlevel.PlayerController.BOX_SPEED;

public class GameContext {

    private final List<MovableItem> boxList;
    private final LevelsLoader levelsLoader;
    private final SoundManager soundManager;
    private GameLevel gameLevel;
    private IGuiInstance guiInstance;

    public GameContext() {
        boxList = new ArrayList<>();
        soundManager = new SoundManager();
        levelsLoader = new LevelsLoader();
    }

    public void addBox(MovableItem box) {
        boxList.add(box);
    }

    public void cleanup() {
        soundManager.cleanup();
    }

    public MovableItem getBox(int col, int row) {
        MovableItem result = null;
        for (MovableItem box : boxList) {
            if (col == box.getCol() && row == box.getRow()) {
                result = box;
                break;
            }
        }
        return result;
    }

    public GameLevel getGameMap() {
        return gameLevel;
    }

    public IGuiInstance getGuiInstance() {
        return guiInstance;
    }

    public LevelsLoader getLevelsLoader() {
        return levelsLoader;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public boolean hasBoxes() {
        return !boxList.isEmpty();
    }

    public boolean moveBox(MovableItem box, int playerCurrentCol, int playerCurrentRow, int playerNextCol, int playerNextRow) {
        boolean result = false;
        int diffCol = playerNextCol - playerCurrentCol;
        int diffRow = playerNextRow - playerCurrentRow;
        int boxNextCol = playerNextCol;
        int boxNextRow = playerNextRow;
        if (diffCol > 0) {
            boxNextCol++;
        } else if (diffCol < 0) {
            boxNextCol--;
        } else if (diffRow < 0) {
            boxNextRow--;
        } else if (diffRow > 0) {
            boxNextRow++;
        }
        GameLevel.TileType tileType = gameLevel.getTileType(boxNextCol, boxNextRow);
        if (getBox(boxNextCol, boxNextRow) == null && (tileType == GameLevel.TileType.FLOOR || tileType == GameLevel.TileType.FINISH)) {
            box.setDestination(boxNextCol, boxNextRow, BOX_SPEED);
            result = true;
        }

        return result;
    }

    public int nextLevel() {
        int result = 0;
        List<LevelsLoader.LevelData> levelDataList = levelsLoader.getLevelDataList();
        int numLevels = levelDataList.size();
        for (int i = 0; i < numLevels; i++) {
            if (levelDataList.get(i).id().equals(gameLevel.getLevelData().id())) {
                result = i + 1;
                break;
            }
        }
        if (result >= levelDataList.size()) {
            result = 0;
        }
        return result;
    }

    public void removeBox(MovableItem box) {
        this.boxList.removeIf(b -> b.getEntity().getId().equals(box.getEntity().getId()));
    }

    public void selectLevel(GameLevel gameLevel) {
        this.gameLevel = gameLevel;
        boxList.clear();
    }

    public void setGuiInstance(IGuiInstance guiInstance) {
        this.guiInstance = guiInstance;
    }

    public void updateBoxes(EngCtx engCtx, long diffTimeMills) {
        MovableItem boxToRemove = null;
        for (MovableItem box : boxList) {
            box.update(diffTimeMills);
            if (!box.isMoving() && gameLevel.getTileType(box.getCol(), box.getRow()) == GameLevel.TileType.FINISH) {
                boxToRemove = box;
            }
        }

        if (boxToRemove != null) {
            removeBox(boxToRemove);
            engCtx.scene().removeEntity(boxToRemove.getEntity().getId());
            soundManager.play(GameUtils.DEFAULT_SOUND_SOURCE, GameUtils.SOUNDS_BOX_FINISH);
        }
    }
}
