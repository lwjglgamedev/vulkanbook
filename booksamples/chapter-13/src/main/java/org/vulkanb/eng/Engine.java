package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private IAppLogic appLogic;
    private Render render;
    private boolean running;
    private Scene scene;
    private Window window;

    public Engine(String windowTitle, IAppLogic appLogic) {
        this.appLogic = appLogic;
        window = new Window(windowTitle);
        scene = new Scene(window);
        render = new Render(window, scene);
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

            scene.getCamera().setHasMoved(false);
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
