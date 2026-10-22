package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Map;

/** One checklist: clickable rows, scan, copy, add items, complete, and delete. Takes the file name, e.g. "Wheat Farm.txt". */
public class ChecklistScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 240;
    private static final int ROWS_START = 64;

    private final String fileName;
    private boolean confirmingDelete = false;

    public ChecklistScreen(String fileName) {
        super(Component.literal("Checklist"));
        this.fileName = fileName;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int left = this.width / 2 - BUTTON_WIDTH / 2;
        Progress progress = new Progress(baseName());

        // Top row
        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new PickerScreen())
        ).bounds(left, 14, 48, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Scan"), b -> {
            scanInventory();
            rebuild();
        }).bounds(left + 52, 14, 48, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Add Item"), b ->
                this.minecraft.gui.setScreen(new CatalogScreen(fileName))
        ).bounds(left + 104, 14, 72, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Copy"), b ->
                FarmListClient.copyToClipboard(fileName)
        ).bounds(left + 180, 14, 60, 20).build());

        // Second row: complete and delete
        addRenderableWidget(Button.builder(Component.literal(progress.isComplete() ? "Reopen" : "Complete"), b -> {
            progress.setComplete(!progress.isComplete());
            FarmListClient.message(progress.isComplete() ? "Marked complete." : "Reopened.");
            rebuild();
        }).bounds(left, 38, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal(confirmingDelete ? "Confirm delete" : "Delete"), b -> {
            if (!confirmingDelete) {
                confirmingDelete = true;
                FarmListClient.message("Press Confirm delete to remove this checklist.");
                rebuild();
            } else {
                FarmListClient.deleteChecklist(fileName);
                this.minecraft.gui.setScreen(new PickerScreen());
            }
        }).bounds(left + 124, 38, 116, 20).build());

        // Checklist rows
        Path file = FarmListClient.TRANSCRIPTION_DIR.resolve(fileName);
        Map<String, Integer> required = FarmListClient.parseRequired(file);

        int y = ROWS_START;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (y > this.height - 30) break;
            String id = e.getKey();
            boolean done = progress.isChecked(id);
            boolean isBlock = FarmListClient.BUILDING_BLOCK.equals(id);
            String shown = isBlock
                    ? "building block (" + displayName(FarmListClient.countedId(id, progress)) + ")"
                    : id;
            String label = (done ? "[x] " : "[ ] ") + shown + "  x" + e.getValue();
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                progress.set(id, !progress.isChecked(id));
                rebuild();
            }).bounds(left, y, isBlock ? BUTTON_WIDTH - 120 : BUTTON_WIDTH, 20).build());

            if (isBlock) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    progress.setChoice(id, previousOption(progress.getChoice(id)));
                    rebuild();
                }).bounds(left + BUTTON_WIDTH - 116, y, 28, 20).build());

                addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
                    progress.setChoice(id, nextOption(progress.getChoice(id)));
                    rebuild();
                }).bounds(left + BUTTON_WIDTH - 86, y, 86, 20).build());
            }
            y += ROW_HEIGHT;
        }
    }

    private String baseName() {
        return fileName.replace(".txt", "");
    }

    private void scanInventory() {
        Map<String, Integer> required = FarmListClient.parseRequired(
                FarmListClient.TRANSCRIPTION_DIR.resolve(fileName));
        Map<String, Integer> have = FarmListClient.countInventory();
        Progress progress = new Progress(baseName());
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            String counted = FarmListClient.countedId(e.getKey(), progress);
            if (FarmListClient.BUILDING_BLOCK.equals(e.getKey()) && progress.getChoice(e.getKey()) == null) {
                continue;
            }
            if (have.getOrDefault(counted, 0) >= e.getValue()) {
                progress.set(e.getKey(), true);
            }
        }
        FarmListClient.message("Scanned your inventory.");
    }

    private static String nextOption(String current) {
        var options = FarmListClient.blockOptions();
        if (current == null) return options.get(0);
        int i = options.indexOf(current);
        return options.get((i + 1) % options.size());
    }

    private static String previousOption(String current) {
        var options = FarmListClient.blockOptions();
        if (current == null) return options.get(options.size() - 1);
        int i = options.indexOf(current);
        return options.get((i - 1 + options.size()) % options.size());
    }

    private static String displayName(String id) {
        return id.replace('_', ' ');
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, Component.literal(baseName()), this.width / 2, 2, 0xFFFFFFFF);
        if (!FarmListClient.status.isEmpty()) {
            g.centeredText(this.font, Component.literal(FarmListClient.status), this.width / 2, this.height - 14, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
