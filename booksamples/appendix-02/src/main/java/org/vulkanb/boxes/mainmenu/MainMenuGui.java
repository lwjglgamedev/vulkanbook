package org.vulkanb.boxes.mainmenu;

import imgui.*;
import imgui.flag.*;
import org.vulkanb.boxes.*;
import org.vulkanb.eng.EngCtx;

import static org.vulkanb.boxes.GameUtils.*;

public class MainMenuGui implements IGuiInstance {

    private static final String TXT_LOAD_GAME_EXIT = "Exit";
    private static final String TXT_LOAD_GAME_LEVEL = "Load Game Level";
    private static final String TXT_START_GAME = "Start First Level";
    private static final String[] TXTS = new String[]{TXT_START_GAME, TXT_LOAD_GAME_LEVEL, TXT_LOAD_GAME_EXIT};

    private int itemHovered = 0;
    private SelectedOption selectedOption;

    public MainMenuGui() {
        selectedOption = SelectedOption.NONE;
    }

    public SelectedOption getSelectedOption() {
        return selectedOption;
    }

    @Override
    public void processGui(EngCtx engCtx, FontsManager fontsManager) {
        ImGui.newFrame();

        ImGui.pushFont(fontsManager.getFont(DEFAULT_FONT));

        ImVec2 textSize = new ImVec2();
        ImGui.calcTextSize(textSize, TXT_LOAD_GAME_LEVEL);
        int windowWidth = (int) textSize.x + PADDING_WIDTH * 2;
        int windowHeight = (int) textSize.y * 3 + PADDING_HEIGHT * 2;
        ImGui.setNextWindowPos(engCtx.window().getWidth() / 2.0f - windowWidth / 2.0f, PADDING_HEIGHT, ImGuiCond.None);

        ImGui.setNextWindowSize(windowWidth, windowHeight);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.8f);
        ImGui.pushStyleColor(ImGuiCol.Border, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.0f, 0.0f, 0.0f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.0f, 0.0f, 0.0f, 1.0f);

        ImGui.begin("START_PANEL", ImGuiWindowFlags.NoDecoration);
        ImGui.pushStyleVar(ImGuiStyleVar.CellPadding, 0.0f, 0.0f);

        ImGui.beginTable("Start", 2, ImGuiTableFlags.SizingFixedFit);
        textSize = new ImVec2();
        ImGui.calcTextSize(textSize, "> ");

        ImGui.tableSetupColumn("Icon", 0, textSize.x);
        int numOptions = TXTS.length;
        for (int i = 0; i < numOptions; i++) {
            ImGui.tableNextRow();
            ImGui.tableSetColumnIndex(0);
            String prefix = "";
            if (itemHovered == i) {
                prefix = "> ";
            }
            ImGui.text(prefix);
            ImGui.nextColumn();
            ImGui.tableSetColumnIndex(1);
            if (ImGui.selectable(TXTS[i])) {
                selectedOption = switch (i) {
                    case 0 -> SelectedOption.START_GAME;
                    case 1 -> SelectedOption.LOAD_GAME;
                    case 2 -> SelectedOption.EXIT_GAME;
                    default -> SelectedOption.NONE;
                };
            }
            if (ImGui.isItemHovered()) {
                itemHovered = i;
            }
        }
        ImGui.endTable();

        ImGui.popStyleVar();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popStyleColor();
        ImGui.popFont();
        ImGui.end();

        ImGui.endFrame();
        ImGui.render();
    }

    public enum SelectedOption {NONE, START_GAME, LOAD_GAME, EXIT_GAME}
}
