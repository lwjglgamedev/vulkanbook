package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.graph.vk.VkUtils;
import org.vulkanb.eng.scene.Scene;
import org.vulkanb.eng.sound.SoundManager;
import org.vulkanb.eng.wnd.Window;

import java.util.concurrent.*;

public class Engine {

    private static final int WORKER_COUNT = 2;
    private final EngCtx engCtx;
    private final ExecutorService executor;
    private final IGameLogic gameLogic;
    private final Phaser phaser = new Phaser(1);
    private final Render render;
    private final Callable<Void> renderTask;
    private int currentRenderFrame;
    private int currentUpdateFrame;

    public Engine(String windowTitle, IGameLogic appLogic) {
        this.gameLogic = appLogic;
        var window = new Window(windowTitle);
        engCtx = new EngCtx(window, new Scene(window), new SoundManager());
        render = new Render(engCtx);
        InitData initData = gameLogic.init(engCtx);
        render.init(engCtx, initData);
        executor = Executors.newFixedThreadPool(WORKER_COUNT);
        renderTask = () -> {
            render.render(engCtx, currentUpdateFrame);
            phaser.arriveAndDeregister();
            return null;
        };
        currentUpdateFrame = 1;
        currentRenderFrame = 0;
    }

    private void cleanup() {
        executor.shutdownNow();
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
        boolean firstExec = true;
        while (!window.shouldClose()) {
            long now = System.currentTimeMillis();
            deltaUpdate += (now - initialTime) / timeU;

            if (!firstExec) {
                phaser.register();
                executor.submit(renderTask);
            }

            window.pollEvents();
            gameLogic.input(engCtx, now - initialTime);
            window.resetInput();

            if (deltaUpdate >= 1) {
                long diffTimeMillis = now - updateTime;
                gameLogic.update(engCtx, diffTimeMillis);
                updateTime = now;
                deltaUpdate--;
            }
            render.updateGlobalBuffers(engCtx, currentUpdateFrame);

            if (!firstExec) {
                phaser.arriveAndAwaitAdvance();
            }

            currentUpdateFrame = (currentUpdateFrame + 1) % VkUtils.MAX_IN_FLIGHT;
            currentRenderFrame = (currentRenderFrame + 1) % VkUtils.MAX_IN_FLIGHT;

            initialTime = now;
            firstExec = false;
        }

        cleanup();
    }
}
