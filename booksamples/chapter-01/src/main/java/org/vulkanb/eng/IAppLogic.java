package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public interface IAppLogic {

    void cleanup();

    void input(Window window, Scene scene, long diffTimeMillis);

    void init(Window window, Scene scene, Render render);

    void update(Window window, Scene scene, long diffTimeMillis);
}
