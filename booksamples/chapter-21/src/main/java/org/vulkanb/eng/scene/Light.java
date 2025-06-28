package org.vulkanb.eng.scene;

import org.joml.Vector4f;

/**
 * For directional lights, the "w" coordinate of the position attribute will be 0. For point lights it will be "1". For directional lights
 * this attribute should be read as a direction from the light to the scene.
 */
public record Light(Vector4f position, Vector4f color) {
}
