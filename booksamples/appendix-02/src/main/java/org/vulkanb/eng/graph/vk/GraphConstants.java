package org.vulkanb.eng.graph.vk;

public final class GraphConstants {

    public static final int FLOAT_LENGTH = 4;
    public static final int INT_LENGTH = 4;
    public static final int SHORT_LENGTH = 2;
    public static final int MAT4X4_SIZE = 16 * FLOAT_LENGTH;
    public static final int MAX_LIGHTS = 10;
    public static final int SHADOW_MAP_CASCADE_COUNT = 3;
    public static final int VEC4_SIZE = 4 * FLOAT_LENGTH;

    private GraphConstants() {
        // Utility class
    }
}
