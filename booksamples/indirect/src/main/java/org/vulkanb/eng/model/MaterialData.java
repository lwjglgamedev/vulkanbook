package org.vulkanb.eng.model;

import org.joml.Vector4f;

public record MaterialData(String id, String texturePath, String normalMapPath, String metalRoughMap,
                           Vector4f diffuseColor, float roughnessFactor, float metallicFactor) {

    public MaterialData() {
        this("", "", "", "", new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), 0.0f, 0.0f);
    }
}
