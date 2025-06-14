package org.vulkanb.boxes;

import org.joml.Vector3f;
import org.tinylog.Logger;
import org.vulkanb.eng.scene.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

public class GameLevel {

    private final List<MovableItem> movableItems;
    private LevelsLoader.LevelData levelData;
    private List<List<TileType>> tiles;

    public GameLevel(LevelsLoader.LevelData levelData, Scene scene) {
        this.levelData = levelData;
        tiles = new ArrayList<>();
        movableItems = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(new File(levelData.file()).toPath(), Charset.defaultCharset());
            tiles = new ArrayList<>();
            int row = 0;
            for (String line : lines) {
                List<TileType> tileTypes = new ArrayList<>();
                tiles.add(tileTypes);
                int numCols = line.length();
                for (int col = 0; col < numCols; col++) {
                    TileType tileType;
                    String tileModelId;
                    switch (line.charAt(col)) {
                        case '*' -> {
                            tileType = TileType.WALL;
                            tileModelId = GameUtils.WALL_MODEl_ID;
                        }
                        case 'F' -> {
                            tileType = TileType.FINISH;
                            tileModelId = GameUtils.FLOOR_MODEl_ID;
                        }
                        case 'P' -> {
                            tileType = TileType.FLOOR;
                            tileModelId = GameUtils.FLOOR_MODEl_ID;
                            Entity playerEntity = new Entity("player", GameUtils.PLAYER_MODEl_ID,
                                    new Vector3f(col, 0.0f, row));
                            scene.addEntity(playerEntity);
                            playerEntity.setEntityAnimation(new EntityAnimation(true, PlayerAnim.IDLE.getValue(), 0));
                            playerEntity.getRotation().rotateY((float) Math.toRadians(90.0f));
                            playerEntity.updateModelMatrix();
                            movableItems.add(new MovableItem(MovableItem.MovableItemType.PLAYER, playerEntity, col, row));
                        }
                        case '#' -> {
                            tileType = TileType.FLOOR;
                            tileModelId = GameUtils.FLOOR_MODEl_ID;
                            Entity boxEntity = new Entity("box_" + row + "_" + col, GameUtils.BOX_MODEl_ID, new Vector3f(col, 0.0f, row));
                            scene.addEntity(boxEntity);
                            movableItems.add(new MovableItem(MovableItem.MovableItemType.BOX, boxEntity, col, row));
                        }
                        default -> {
                            tileType = TileType.FLOOR;
                            tileModelId = GameUtils.FLOOR_MODEl_ID;
                        }
                    }
                    Entity entity = new Entity("map_entity_" + row + "_" + col, tileModelId, new Vector3f(col, 0.0f, row));
                    scene.addEntity(entity);
                    tileTypes.add(tileType);
                }
                row++;
            }
        } catch (IOException excp) {
            Logger.error("Error reading map file", excp);
            throw new RuntimeException(excp);
        }
    }

    public LevelsLoader.LevelData getLevelData() {
        return levelData;
    }

    public List<MovableItem> getMovableItems() {
        return movableItems;
    }

    public TileType getTileType(int col, int row) {
        TileType result = TileType.WALL;
        if (row >= 0 && row < tiles.size()) {
            List<TileType> rowList = tiles.get(row);
            if (col >= 0 && col < rowList.size()) {
                result = rowList.get(col);
            }
        }
        return result;
    }

    public enum TileType {
        WALL, FLOOR, FINISH
    }
}
