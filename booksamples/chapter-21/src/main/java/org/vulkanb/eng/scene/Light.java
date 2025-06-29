package org.vulkanb.eng.scene;

import org.joml.Vector3f;

public class Light {
    private final Vector3f color;
    private final boolean directional;
    private final Vector3f position;
    private float intensity;

    public Light(Vector3f position, boolean directional, float intensity, Vector3f color) {
        this.position = position;
        this.directional = directional;
        this.intensity = intensity;
        this.color = color;
    }

    public Vector3f getColor() {
        return color;
    }

    public float getIntensity() {
        return intensity;
    }

    public Vector3f getPosition() {
        return position;
    }

    public boolean isDirectional() {
        return directional;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }
}
