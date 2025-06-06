package org.vulkanb.eng.graph.shadow;

import org.joml.*;
import org.vulkanb.eng.scene.Scene;

import java.util.*;

public class CascadeShadows {

    private final List<CascadeData> cascadeData;
    private final Vector3f prevCamPos;
    private final Vector4f prevLightPos;

    public CascadeShadows() {
        prevCamPos = new Vector3f(Float.NEGATIVE_INFINITY);
        prevLightPos = new Vector4f(Float.NEGATIVE_INFINITY);

        cascadeData = new ArrayList<>();
        for (int i = 0; i < Scene.SHADOW_MAP_CASCADE_COUNT; i++) {
            cascadeData.add(new CascadeData());
        }
    }

    public boolean frustumUpdate(Vector3f camPos, Vector4f lightPos) {
        if (camPos.equals(prevCamPos) && lightPos.equals(prevLightPos)) {
            // No need to recalculate
            return false;
        }
        prevCamPos.set(camPos);
        prevLightPos.set(lightPos);
        return true;
    }

    public List<CascadeData> getCascadeData() {
        return cascadeData;
    }
}

