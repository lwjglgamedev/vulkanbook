package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.wnd.Window;

public interface IGameLogic {

    void cleanup();

    void init(EngCtx engCtx);

    void input(EngCtx engCtx, long diffTimeMillis);

    void update(EngCtx engCtx, long diffTimeMillis);
}
