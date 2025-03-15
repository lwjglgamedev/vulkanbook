package org.vulkanb.eng.graph;

public record VulkanMesh(int verticesSize, int numIndices, int verticesOffset, int indicesOffset,
                         int materialIdx, int weightsOffset) {
}