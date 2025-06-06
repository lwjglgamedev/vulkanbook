package org.vulkanb.eng.model;

import java.util.List;

public record ModelData(String id, List<MeshData> meshes, List<AnimMeshData> animMeshDataList,
                        List<Animation> animationsList) {

    public boolean hasAnimations() {
        return animMeshDataList != null && !animMeshDataList.isEmpty();
    }
}
