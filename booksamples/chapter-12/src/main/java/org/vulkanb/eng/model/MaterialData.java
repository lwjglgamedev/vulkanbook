package org.vulkanb.eng.model;

import org.joml.Vector4f;

public record MaterialData(String id, String texturePath, Vector4f diffuseColor) {
}
