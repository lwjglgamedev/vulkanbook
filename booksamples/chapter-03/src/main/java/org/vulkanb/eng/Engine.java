package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.wnd.Window;

public class Engine {

    private final EngCtx engCtx;
    private final IGameLogic gameLogic;
    private final Render render;

    public Engine(String windowTitle, IGameLogic appLogic) {
        this.gameLogic = appLogic;
        var window = new Window(windowTitle);
        engCtx = new EngCtx(window, new Scene(window));
        render = new Render(engCtx);
        gameLogic.init(engCtx);
    }

    private void cleanup() {
        gameLogic.cleanup();
        render.cleanup();
        engCtx.cleanup();
    }

    public void run() {
        var engCfg = EngCfg.getInstance();
        long initialTime = System.currentTimeMillis();
        float timeU = 1000.0f / engCfg.getUps();
        double deltaUpdate = 0;

        long updateTime = initialTime;
        Window window = engCtx.window();
        while (!window.shouldClose()) {
            long now = System.currentTimeMillis();
            deltaUpdate += (now - initialTime) / timeU;

            window.pollEvents();
            gameLogic.input(engCtx, now - initialTime);
            window.resetInput();

            if (deltaUpdate >= 1) {
                long diffTimeMillis = now - updateTime;
                gameLogic.update(engCtx, diffTimeMillis);
                updateTime = now;
                deltaUpdate--;
            }

            render.render(engCtx);

            initialTime = now;
        }

        cleanup();
    }
}
