package org.vulkanb.eng.model;

import org.joml.Matrix4f;

public record AnimatedFrame(Matrix4f[] jointMatrices) {
}