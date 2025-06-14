package org.vulkanb.boxes.runlevel;

import imgui.*;
import imgui.flag.*;
import org.vulkanb.boxes.*;
import org.vulkanb.eng.EngCtx;

import static org.vulkanb.boxes.GameUtils.*;

public class NextLevelGui implements IGuiInstance {
    private static final String TXT_CONTINUE = "Continue ?";
    private boolean dontContinue;
    private boolean nextLevel;

    public boolean isDontContinue() {
        return dontContinue;
    }

    public boolean isNextLevel() {
        return nextLevel;
    }

    @Override
    public void processGui(EngCtx engCtx, FontsManager fontsManager) {
        ImGui.newFrame();

        ImGui.pushFont(fontsManager.getFont(DEFAULT_FONT));

        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, TXT_CONTINUE);
        int windowWidth = (int) textSize.x + PADDING_WIDTH * 2;
        int windowHeight = ((int) textSize.y + PADDING_HEIGHT * 2) * 2;
        ImGui.setNextWindowPos(engCtx.window().getWidth() / 2.0f - windowWidth / 2.0f, PADDING_HEIGHT, ImGuiCond.None);

        ImGui.setNextWindowSize(windowWidth, windowHeight);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.4f, 0.4f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);

        ImGui.begin("NEXT_PANEL", ImGuiWindowFlags.NoDecoration);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0.0f, 0.0f);

        ImGui.text(TXT_CONTINUE);
        ImGui.newLine();

        ImGui.beginTable("Select", 2);
        ImGui.tableNextRow();
        ImGui.tableSetColumnIndex(0);
        nextLevel = ImGui.button("Yes");
        ImGui.tableSetColumnIndex(1);
        dontContinue = ImGui.button("No");

        ImGui.endTable();

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
}