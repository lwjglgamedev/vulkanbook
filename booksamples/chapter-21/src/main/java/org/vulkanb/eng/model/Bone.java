package org.vulkanb.eng.model;

import org.joml.Matrix4f;

public record Bone(int boneId, String boneName, Matrix4f offsetMatrix) {
}
