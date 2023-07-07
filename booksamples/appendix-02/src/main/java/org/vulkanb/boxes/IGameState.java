package org.vulkanb.boxes;

import org.vulkanb.eng.Window;
import org.vulkanb.eng.scene.Scene;

public interface IGameState {

    IGameState processInput(Window window, Scene scene, GameContext gameContext, long diffTimeMillis);

    void update(Scene scene, GameContext gameContext, long diffTimeMillis);
}
