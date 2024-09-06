package org.vulkanb.eng;

import imgui.*;
import org.joml.Vector2f;
import org.vulkanb.eng.graph.Render;
import org.vulkanb.eng.graph.gui.GuiRenderActivity;
import org.vulkanb.eng.scene.Scene;

public class Engine {

    private final IAppLogic appLogic;
    private final Render render;
    private final Scene scene;
    private final Window window;
    private boolean running;

    public Engine(String windowTitle, IAppLogic appLogic) {
        this.appLogic = appLogic;
        window = new Window(windowTitle, new GuiRenderActivity.KeyCallback(), new GuiRenderActivity.CharCallBack());
        scene = new Scene(window);
        render = new Render(window, scene);
        appLogic.init(window, scene, render);
    }

    private void cleanup() {
        appLogic.cleanup();
        render.cleanup();
        window.cleanup();
    }

    private boolean handleInputGui() {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = window.getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.addMousePosEvent(mousePos.x, mousePos.y);
        imGuiIO.addMouseButtonEvent(0, mouseInput.isLeftButtonPressed());
        imGuiIO.addMouseButtonEvent(1, mouseInput.isRightButtonPressed());

        return imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
    }

    public void run() {
        EngineProperties engineProperties = EngineProperties.getInstance();
        long initialTime = System.currentTimeMillis();
        float timeU = 1000.0f / engineProperties.getUps();
        double deltaUpdate = 0;

        long updateTime = initialTime;
        while (running && !window.shouldClose()) {
            scene.getCamera().setHasMoved(false);
            window.pollEvents();

            long now = System.currentTimeMillis();
            deltaUpdate += (now - initialTime) / timeU;

            boolean inputConsumed = handleInputGui();
            appLogic.input(window, scene, now - initialTime, inputConsumed);

            if (deltaUpdate >= 1) {
                long diffTimeMilis = now - updateTime;
                appLogic.update(window, scene, diffTimeMilis);
                updateTime = now;
                deltaUpdate--;
            }

            render.render(window, scene);

            initialTime = now;
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
