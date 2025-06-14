package org.vulkanb.eng.model;

import java.util.List;

public record ModelData(String id, List<MeshData> meshes, String vtxPath, String idxPath, List<AnimMeshData> animMeshes,
                        List<Animation> animations) {
}
