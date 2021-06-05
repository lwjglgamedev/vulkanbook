package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public interface IAppLogic {

    void cleanup();

    void handleInput(Window window, Scene scene, long diffTimeMillis, boolean inputConsumed);

    void init(Window window, Scene scene, Render render);
}
