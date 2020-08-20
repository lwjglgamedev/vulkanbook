package org.vulkanb.eng.scene;

public record MeshData(String id, float[]positions, float[]textCoords, int[]indices, Material material) {
}
