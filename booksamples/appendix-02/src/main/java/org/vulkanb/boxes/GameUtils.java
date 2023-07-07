package org.vulkanb.boxes;

import org.joml.Vector3f;
import org.vulkanb.eng.scene.Entity;

public class GameUtils {
    public static final String BOX_MODEl_ID = "box-model";
    public static final String DEFAULT_FONT = "MAIN";
    public static final String DEFAULT_SOUND_SOURCE = "default-sound-source";
    public static final String FLOOR_MODEl_ID = "floor-model";
    public static final int PADDING_HEIGHT = 50;
    public static final int PADDING_WIDTH = 50;
    public static final String PLAYER_MODEl_ID = "player-model";
    public static final String SOUNDS_BOX_FINISH = "box-finish";
    public static final String SOUNDS_FOOT_STEP = "foot-step";
    public static final String SOUNDS_SELECT = "select";
    public static final String WALL_MODEl_ID = "wall-model";

    private GameUtils() {
        // Utility class
    }

    public static void setPosition(Entity entity, int col, int row) {
        Vector3f pos = entity.getPosition();
        pos.x = col;
        pos.z = row;
        entity.setPosition(pos.x, pos.y, pos.z);
    }
}
