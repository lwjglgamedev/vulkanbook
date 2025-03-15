package org.vulkanb.eng.model;

public record MeshData(String id, String materialId, int materialIdx, float[] positions, float[] normals,
                       float[] tangents, float[] biTangents, float[] textCoords, int[] indices) {
}
