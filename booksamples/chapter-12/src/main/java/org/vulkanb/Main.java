package org.vulkanb;

import imgui.*;
import imgui.flag.ImGuiCond;
import org.joml.*;
import org.tinylog.Logger;
import org.vulkanb.eng.*;
import org.vulkanb.eng.model.*;
import org.vulkanb.eng.scene.*;
import org.vulkanb.eng.wnd.*;

import java.lang.Math;
import java.util.*;

import static org.lwjgl.glfw.GLFW.*;

public class Main implements IGameLogic {

    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float MOVEMENT_SPEED = 0.01f;

    private boolean defaultGui = true;
    private GuiTexture guiTexture;

    public static void main(String[] args) {
        Logger.info("Starting application");
        var engine = new Engine("Vulkan Book", new Main());
        Logger.info("Started application");
        engine.run();
    }

    @Override
    public void cleanup() {
        // To be implemented
    }

    private boolean handleGui(EngCtx engCtx) {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = engCtx.window().getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.addMousePosEvent(mousePos.x, mousePos.y);
        imGuiIO.addMouseButtonEvent(0, mouseInput.isLeftButtonPressed());
        imGuiIO.addMouseButtonEvent(1, mouseInput.isRightButtonPressed());

        if (defaultGui) {
            ImGui.newFrame();
            ImGui.showDemoWindow();
            ImGui.endFrame();
            ImGui.render();
        } else {
            ImGui.newFrame();
            ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
            ImGui.setNextWindowSize(200, 200);
            ImGui.begin("Test Window");
            ImGui.image(guiTexture.id(), new ImVec2(300, 300));
            ImGui.end();
            ImGui.endFrame();
            ImGui.render();
        }

        return imGuiIO.getWantCaptureKeyboard();
    }

    @Override
    public InitData init(EngCtx engCtx) {
        Scene scene = engCtx.scene();
        List<ModelData> models = new ArrayList<>();

        ModelData sponzaModel = ModelLoader.loadModel("resources/models/sponza/Sponza.json");
        models.add(sponzaModel);
        Entity sponzaEntity = new Entity("SponzaEntity", sponzaModel.id(), new Vector3f(0.0f, 0.0f, 0.0f));
        scene.addEntity(sponzaEntity);

        List<MaterialData> materials = new ArrayList<>(ModelLoader.loadMaterials("resources/models/sponza/Sponza_mat.json"));

        guiTexture = new GuiTexture("resources/textures/vulkan.png");
        List<GuiTexture> guiTextures = new ArrayList<>();
        guiTextures.add(guiTexture);

        Camera camera = scene.getCamera();
        camera.setPosition(0.0f, 5.0f, 0.0f);
        camera.setRotation((float) Math.toRadians(20.0f), (float) Math.toRadians(90.f));

        return new InitData(models, materials, guiTextures);
    }

    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        if (handleGui(engCtx)) {
            return;
        }

        Scene scene = engCtx.scene();
        Window window = engCtx.window();

        KeyboardInput ki = window.getKeyboardInput();
        float move = diffTimeMillis * MOVEMENT_SPEED;
        Camera camera = scene.getCamera();
        if (ki.keyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        } else if (ki.keyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (ki.keyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        } else if (ki.keyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }
        if (ki.keyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move);
        } else if (ki.keyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move);
        }

        if (ki.keyPressed(GLFW_KEY_1)) {
            defaultGui = true;
        } else if (ki.keyPressed(GLFW_KEY_2)) {
            defaultGui = false;
        }

        MouseInput mi = window.getMouseInput();
        if (mi.isRightButtonPressed()) {
            Vector2f deltaPos = mi.getDeltaPos();
            camera.addRotation((float) Math.toRadians(-deltaPos.y * MOUSE_SENSITIVITY),
                    (float) Math.toRadians(-deltaPos.x * MOUSE_SENSITIVITY));
        }
    }

    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        // To be implemented
    }
}
