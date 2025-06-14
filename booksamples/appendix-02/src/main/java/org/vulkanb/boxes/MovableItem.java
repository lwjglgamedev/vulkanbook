package org.vulkanb.boxes;

import org.joml.Vector3f;
import org.vulkanb.eng.scene.Entity;

public class MovableItem {
    private int col;
    private Entity entity;
    private MovableItemType movableItemType;
    private MoveDir moveDir;
    private int row;
    private float speed;

    public MovableItem(MovableItemType movableItemType, Entity entity, int col, int row) {
        this.movableItemType = movableItemType;
        this.entity = entity;
        this.col = col;
        this.row = row;
        moveDir = MoveDir.NONE;
    }

    public int getCol() {
        return col;
    }

    public Entity getEntity() {
        return entity;
    }

    public MovableItemType getMovableItemType() {
        return movableItemType;
    }

    public int getRow() {
        return row;
    }

    public boolean isMoving() {
        return moveDir != MoveDir.NONE;
    }

    // TODO: Check this
    public void move(int col, int row) {
        this.col = col;
        this.row = row;
        GameUtils.setPosition(entity, col, row);
    }

    public void setDestination(int col, int row, float speed) {
        if (col < this.col) {
            moveDir = MoveDir.LEFT;
        } else if (col > this.col) {
            moveDir = MoveDir.RIGHT;
        } else if (row < this.row) {
            moveDir = MoveDir.UP;
        } else if (row > this.row) {
            moveDir = MoveDir.DOWN;
        }

        this.col = col;
        this.row = row;
        this.speed = speed;
    }

    public void update(long diffMillis) {
        if (moveDir == MoveDir.NONE) {
            return;
        }
        float inc = diffMillis * speed;
        Vector3f entityPos = entity.getPosition();
        switch (moveDir) {
            case LEFT -> {
                float posX = entityPos.x - inc;
                float dest = col;
                if (posX <= dest) {
                    posX = dest;
                    moveDir = MoveDir.NONE;
                }
                entity.setPosition(posX, entityPos.y, entityPos.z);
            }
            case RIGHT -> {
                float posX = entityPos.x + inc;
                float dest = col;
                if (posX >= dest) {
                    posX = dest;
                    moveDir = MoveDir.NONE;
                }
                entity.setPosition(posX, entityPos.y, entityPos.z);
            }
            case UP -> {
                float posZ = entityPos.z - inc;
                float dest = row;
                if (posZ <= dest) {
                    posZ = dest;
                    moveDir = MoveDir.NONE;
                }
                entity.setPosition(entityPos.x, entityPos.y, posZ);
            }
            case DOWN -> {
                float posZ = entityPos.z + inc;
                float dest = row;
                if (posZ >= dest) {
                    posZ = dest;
                    moveDir = MoveDir.NONE;
                }
                entity.setPosition(entityPos.x, entityPos.y, posZ);
            }
        }
    }

    public enum MovableItemType {PLAYER, BOX}

    private enum MoveDir {NONE, LEFT, RIGHT, UP, DOWN}
}
