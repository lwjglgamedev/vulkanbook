package org.vulkanb.boxes.runlevel;

import org.vulkanb.boxes.*;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.wnd.KeyboardInput;

import static org.lwjgl.glfw.GLFW.*;

public class PlayerController {

    public static final float BOX_SPEED = 0.001f;
    private static final float PLAYER_SPEED = 0.002f;
    private final MovableItem player;
    private boolean playerMoving;

    public PlayerController(MovableItem player) {
        this.player = player;
        playerMoving = false;
    }

    public void input(EngCtx engCtx, GameContext gameContext) {
        if (playerMoving) {
            return;
        }
        int origCol = player.getCol();
        int origRow = player.getRow();
        int col = origCol;
        int row = origRow;
        boolean keyPressed = false;
        Entity playerEntity = player.getEntity();
        KeyboardInput ki = engCtx.window().getKeyboardInput();
        if (ki.keyPressed(GLFW_KEY_LEFT)) {
            col--;
            keyPressed = true;
            playerEntity.resetRotation();
            playerEntity.getRotation().rotateY((float) Math.toRadians(-90.0f));
        } else if (ki.keyPressed(GLFW_KEY_RIGHT)) {
            col++;
            keyPressed = true;
            playerEntity.resetRotation();
            playerEntity.getRotation().rotateY((float) Math.toRadians(90.0f));
        } else if (ki.keyPressed(GLFW_KEY_UP)) {
            row--;
            keyPressed = true;
            playerEntity.resetRotation();
            playerEntity.getRotation().rotateY((float) Math.toRadians(180.0f));
        } else if (ki.keyPressed(GLFW_KEY_DOWN)) {
            row++;
            keyPressed = true;
            playerEntity.resetRotation();
        }
        if (!keyPressed) {
            if (playerEntity.getEntityAnimation().getAnimationIdx() != PlayerAnim.IDLE.getValue()) {
                playerEntity.setEntityAnimation(new EntityAnimation(true, PlayerAnim.IDLE.getValue(), 0));
            }
            return;
        }
        GameLevel gameLevel = gameContext.getGameMap();
        GameLevel.TileType tileType = gameLevel.getTileType(col, row);
        if (tileType == GameLevel.TileType.WALL) {
            return;
        }
        MovableItem box = gameContext.getBox(col, row);
        if (box != null && gameContext.moveBox(box, origCol, origRow, col, row)) {
            if (playerEntity.getEntityAnimation().getAnimationIdx() != PlayerAnim.PUSHING.getValue()) {
                playerEntity.setEntityAnimation(new EntityAnimation(true, PlayerAnim.PUSHING.getValue(), 0));
            }
            playerMoving = true;
            player.setDestination(col, row, BOX_SPEED);
            gameContext.getSoundManager().play(GameUtils.DEFAULT_SOUND_SOURCE, GameUtils.SOUNDS_FOOT_STEP);
        } else if (box == null) {
            if (playerEntity.getEntityAnimation().getAnimationIdx() != PlayerAnim.WALKING.getValue()) {
                playerEntity.setEntityAnimation(new EntityAnimation(true, PlayerAnim.WALKING.getValue(), 0));
            }
            playerMoving = true;
            player.setDestination(col, row, PLAYER_SPEED);
            gameContext.getSoundManager().play(GameUtils.DEFAULT_SOUND_SOURCE, GameUtils.SOUNDS_FOOT_STEP);
        }
    }

    public void update(long diffTimeMillis) {
        if (!playerMoving) {
            return;
        }
        player.update(diffTimeMillis);
        playerMoving = player.isMoving();
    }
}
