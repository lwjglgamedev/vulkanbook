package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private boolean running;
    private IAppLogic appLogic;
    private Render render;
    private Scene scene;
    private Window window;

    public Engine(String windowTitle, IAppLogic gameLogic) {
        window = new Window(windowTitle);
        render = new Render();
        appLogic = gameLogic;
        scene = new Scene(window);
        render.init(window);
        appLogic.init(window, scene, render);
    }

    private void cleanup() {
        appLogic.cleanup();
        render.cleanup();
        window.cleanup();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.nanoTime();
        double timeU = 1000000000d / engineProperties.getUps();
        double deltaU = 0;

        long updateTime = initialTime;
        while (running && !window.shouldClose()) {

            window.pollEvents();

            long currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                long diffTimeNanos = currentTime - updateTime;
                appLogic.handleInput(window, scene, diffTimeNanos);
                updateTime = currentTime;
                deltaU--;
            }

            render.render(window, scene);
        }

        cleanup();
    }

    public void start() {
        running = true;
        run();
    }

    public void stop() {
        running = false;
    }
}
