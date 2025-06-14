package org.vulkanb.boxes;

import org.vulkanb.eng.EngCtx;

public interface IGameState {

    IGameState processInput(EngCtx engCtx, GameContext gameContext, long diffTimeMillis);

    void update(EngCtx engCtx, GameContext gameContext, long diffTimeMillis);
}
