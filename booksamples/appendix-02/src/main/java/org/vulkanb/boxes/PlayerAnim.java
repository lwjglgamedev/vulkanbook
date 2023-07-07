package org.vulkanb.boxes;

public enum PlayerAnim {
    IDLE(0), WALKING(2), PUSHING(1);

    private final int value;

    private PlayerAnim(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
