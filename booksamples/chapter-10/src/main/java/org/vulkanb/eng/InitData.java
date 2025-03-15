package org.vulkanb.eng;

import org.vulkanb.eng.model.*;

import java.util.List;

public record InitData(List<ModelData> models, List<MaterialData> materials) {
}
