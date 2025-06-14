package org.vulkanb.boxes.selectlevel;

import imgui.*;
import imgui.flag.*;
import org.vulkanb.boxes.*;
import org.vulkanb.eng.EngCtx;
import org.vulkanb.eng.wnd.Window;

import java.util.List;

import static org.vulkanb.boxes.GameUtils.*;

public class SelectLevelGui implements IGuiInstance {

    private static final String TXT_SELECT_LEVEL = "Select Level:";
    private final List<LevelsLoader.LevelData> levelDataList;
    private boolean backSelected = false;
    private int itemHovered = 0;
    private int itemSelected = -1;

    public SelectLevelGui(List<LevelsLoader.LevelData> levelDataList) {
        this.levelDataList = levelDataList;
    }

    public int getItemSelected() {
        return itemSelected;
    }

    public boolean isBackSelected() {
        return backSelected;
    }

    @Override
    public void processGui(EngCtx engCtx, FontsManager fontsManager) {
        ImGui.newFrame();

        ImGui.pushFont(fontsManager.getFont(DEFAULT_FONT));

        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, TXT_SELECT_LEVEL);
        int windowWidth = (int) textSize.x + PADDING_WIDTH * 2;
        Window window = engCtx.window();
        int windowHeight = window.getHeight();
        ImGui.setNextWindowPos(window.getWidth() / 2.0f - windowWidth / 2.0f, PADDING_HEIGHT, ImGuiCond.None);

        ImGui.setNextWindowSize(windowWidth, windowHeight);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.Button, 0.0f, 0.0f, 0.0f, 0.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.4f, 0.4f, 0.4f, 0.5f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.2f, 0.2f, 0.5f);

        ImGui.begin("SELECT_LEVEL_PANEL", ImGuiWindowFlags.NoDecoration);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0.0f, 0.0f);

        ImGui.text(TXT_SELECT_LEVEL);
        ImGui.newLine();

        ImGui.beginTable("Select", 2, ImGuiTableFlags.SizingFixedFit);
        textSize = new ImVec2();
        ImGui.calcTextSize(textSize, "> ");

        ImGui.tableSetupColumn("Icon", 0, textSize.x);
        int numLevels = levelDataList != null ? levelDataList.size() : 0;
        for (int i = 0; i < numLevels; i++) {
            ImGui.tableNextRow();
            ImGui.tableSetColumnIndex(0);
            String prefix = "";
            if (itemHovered == i) {
                prefix = "> ";
            }
            ImGui.text(prefix);
            ImGui.nextColumn();
            ImGui.tableSetColumnIndex(1);
            if (ImGui.selectable(levelDataList.get(i).id())) {
                itemSelected = i;
            }
            if (ImGui.isItemHovered()) {
                itemHovered = i;
            }
        }
        ImGui.endTable();

        ImGui.newLine();
        backSelected = ImGui.button("Back");

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

    public void reset() {
        itemSelected = -1;
    }
}
