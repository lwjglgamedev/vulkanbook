package org.vulkanb.eng;

public interface IGameLogic {

    void cleanup();

    void init(EngCtx engCtx);

    void input(EngCtx engCtx, long diffTimeMillis);

    void update(EngCtx engCtx, long diffTimeMillis);
}
