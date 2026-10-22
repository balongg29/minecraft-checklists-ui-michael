package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The checklist picker. Each checklist has a color square: gray = not started, yellow = in progress, green = complete. */
public class PickerScreen extends Screen {

    private static final int BUTTON_WIDTH = 240;
    private static final int ROW_HEIGHT = 22;

    static final int COLOR_NOT_STARTED = 0xFF888888;
    static final int COLOR_IN_PROGRESS = 0xFFFFD755;
    static final int COLOR_COMPLETE = 0xFF55FF55;

    /** Where each list's color square goes, filled in during init. */
    private final List<int[]> squares = new ArrayList<>();   // x, y
    private final List<Integer> squareColors = new ArrayList<>();

    public PickerScreen() {
        super(Component.literal("Farm Checklist"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        squares.clear();
        squareColors.clear();
        int left = this.width / 2 - BUTTON_WIDTH / 2;

        addRenderableWidget(Button.builder(Component.literal("New Checklist"), b ->
                this.minecraft.gui.setScreen(new NameScreen())
        ).bounds(left, 32, BUTTON_WIDTH, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Import from Clipboard"), b -> {
            String imported = FarmListClient.importFromClipboard();
            if (imported != null) this.minecraft.gui.setScreen(new ChecklistScreen(imported));
        }).bounds(left, 56, BUTTON_WIDTH, 20).build());

        int y = 84;
        for (String file : FarmListClient.listTranscriptions()) {
            String name = file.replace(".txt", "");
            Progress progress = new Progress(name);
            Map<String, Integer> required = FarmListClient.parseRequired(
                    FarmListClient.TRANSCRIPTION_DIR.resolve(file));

            int total = required.size();
            int checked = 0;
            for (String id : required.keySet()) {
                if (progress.isChecked(id)) checked++;
            }

            int color;
            if (progress.isComplete()) {
                color = COLOR_COMPLETE;
            } else if (checked > 0) {
                color = COLOR_IN_PROGRESS;
            } else {
                color = COLOR_NOT_STARTED;
            }

            String label = name + "  " + checked + "/" + total;
            addRenderableWidget(Button.builder(Component.literal(label), b ->
                    this.minecraft.gui.setScreen(new ChecklistScreen(file))
            ).bounds(left + 14, y, BUTTON_WIDTH - 14, 20).build());

            squares.add(new int[] { left, y + 4 });
            squareColors.add(color);
            y += ROW_HEIGHT;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, Component.literal("Your Checklists"), this.width / 2, 14, 0xFFFFFFFF);

        // Color squares: gray = not started, yellow = in progress, green = complete
        for (int i = 0; i < squares.size(); i++) {
            int x = squares.get(i)[0];
            int y = squares.get(i)[1];
            g.fill(x, y, x + 8, y + 8, squareColors.get(i));
        }

        if (!FarmListClient.status.isEmpty()) {
            g.centeredText(this.font, Component.literal(FarmListClient.status), this.width / 2, this.height - 14, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
