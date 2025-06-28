package org.vulkanb.eng.graph.shadow;

import org.vulkanb.eng.scene.Scene;

import java.util.*;

public class CascadeShadows {

    private final List<CascadeData> cascadeData;

    public CascadeShadows() {
        cascadeData = new ArrayList<>();
        for (int i = 0; i < Scene.SHADOW_MAP_CASCADE_COUNT; i++) {
            cascadeData.add(new CascadeData());
        }
    }

    public List<CascadeData> getCascadeData() {
        return cascadeData;
    }
}

