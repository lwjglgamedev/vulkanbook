package org.vulkanb.eng;

import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private IAppLogic appLogic;
    private Render render;
    private boolean running;
    private Scene scene;
    private Window window;

    public Engine(String windowTitle, IAppLogic gameLogic) {
        this.window = new Window(windowTitle);
        this.render = new Render();
        this.appLogic = gameLogic;
        this.scene = new Scene(this.window);
        this.render.init(this.window);
        this.appLogic.init(this.window, this.scene, this.render);
    }

    private void cleanUp() {
        this.appLogic.cleanUp();
        this.render.cleanUp();
        this.window.cleanUp();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.nanoTime();
        double timeU = 1000000000d / engineProperties.getUps();
        double deltaU = 0;

        long updateTime = initialTime;
        while (this.running && !this.window.shouldClose()) {

            this.window.pollEvents();

            long currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                long diffTimeNanos = currentTime - updateTime;
                this.appLogic.handleInput(this.window, this.scene, diffTimeNanos);
                updateTime = currentTime;
                deltaU--;
            }

            this.render.render(this.window, this.scene);
        }

        cleanUp();
    }

    public void start() {
        this.running = true;
        run();
    }

    public void stop() {
        this.running = false;
    }
}
