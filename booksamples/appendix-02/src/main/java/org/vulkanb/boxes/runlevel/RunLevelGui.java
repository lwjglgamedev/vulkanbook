package org.vulkanb.boxes.runlevel;

import imgui.*;
import imgui.flag.*;
import org.vulkanb.boxes.GameLevel;
import org.vulkanb.eng.Window;
import org.vulkanb.eng.graph.gui.FontsManager;
import org.vulkanb.eng.scene.IGuiInstance;

import static org.vulkanb.boxes.GameUtils.*;

public class RunLevelGui implements IGuiInstance {

    private static final String TXT_BACK = "BACK";
    private final GameLevel gameLevel;
    private final Window window;
    private boolean backPressed = false;

    public RunLevelGui(Window window, GameLevel gameLevel) {
        this.window = window;
        this.gameLevel = gameLevel;
    }

    @Override
    public void drawGui(FontsManager fontsManager) {
        ImGui.newFrame();

        ImGui.pushFont(fontsManager.getFont(DEFAULT_FONT));

        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, "A");
        int windowHeight = (int) textSize.y + PADDING_HEIGHT;
        ImGui.setNextWindowPos(0.0f, 0.0f, ImGuiCond.None);

        ImGui.setNextWindowSize(window.getWidth(), windowHeight);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.4f, 0.4f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);

        ImGui.begin("RUN_LEVEL_PANEL", ImGuiWindowFlags.NoDecoration);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0.0f, 0.0f);

        ImGui.text(gameLevel.getLevelData().id());
        ImGui.sameLine();
        ImGui.calcTextSize(textSize, TXT_BACK);
        ImGui.setCursorPosX(window.getWidth() - textSize.x - PADDING_WIDTH);
        backPressed = ImGui.button(TXT_BACK);

        ImGui.popStyleVar();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popFont();
        ImGui.end();

        ImGui.endFrame();
        ImGui.render();

    }

    public boolean isBackPressed() {
        return backPressed;
    }
}
