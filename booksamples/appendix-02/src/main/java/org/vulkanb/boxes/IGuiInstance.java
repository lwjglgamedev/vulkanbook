package org.vulkanb.boxes;

import imgui.*;
import org.joml.Vector2f;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.wnd.MouseInput;

public interface IGuiInstance {
    default boolean defaultGuiInput(EngCtx engCtx) {
        ImGuiIO imGuiIO = ImGui.getIO();
        MouseInput mouseInput = engCtx.window().getMouseInput();
        Vector2f mousePos = mouseInput.getCurrentPos();
        imGuiIO.addMousePosEvent(mousePos.x, mousePos.y);
        imGuiIO.addMouseButtonEvent(0, mouseInput.isLeftButtonPressed());
        imGuiIO.addMouseButtonEvent(1, mouseInput.isRightButtonPressed());

        return imGuiIO.getWantCaptureKeyboard();
    }

    void processGui(EngCtx engCtx, FontsManager fontsManager);
}
