package org.vulkanb.eng;

public interface IGameLogic {

    void cleanup();

    InitData init(EngCtx engCtx);

    void input(EngCtx engCtx, long diffTimeMillis);

    void update(EngCtx engCtx, long diffTimeMillis);
    
}
