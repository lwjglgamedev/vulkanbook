package org.vulkanb.eng.scene;

public record MeshData(String id, float[] positions, float[] normals, float[] tangents, float[] biTangents,
                       float[] textCoords, int[] indices, Material material) {
}
