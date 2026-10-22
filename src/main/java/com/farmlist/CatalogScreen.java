package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search every item in the game, pick one, and choose how many to add to the checklist. */
public class CatalogScreen extends Screen {

    private static final int PAGE_SIZE = 8;
    private static final int ROW_HEIGHT = 22;

    private final String fileName;
    private EditBox searchBox;
    private EditBox countBox;
    private List<Item> results = new ArrayList<>();
    private Item selected = null;
    private int page = 0;

    public CatalogScreen(String fileName) {
        super(Component.literal("Add Item"));
        this.fileName = fileName;
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, this.width / 2 - 120, 30, 240, 20, Component.literal("Search"));
        searchBox.setResponder(text -> { page = 0; selected = null; refresh(); });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        countBox = new EditBox(this.font, this.width / 2 - 120, this.height - 60, 60, 20, Component.literal("Count"));
        countBox.setValue("1");
        countBox.setMaxLength(4);
        addRenderableWidget(countBox);

        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addSelected())
                .bounds(this.width / 2 - 52, this.height - 60, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new ChecklistScreen(fileName))
        ).bounds(this.width / 2 + 16, this.height - 60, 60, 20).build());

        refresh();
    }

    private void refresh() {
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        results = new ArrayList<>();
        for (Item item : FarmListClient.allItems()) {
            String id = FarmListClient.itemId(item);
            String name = FarmListClient.itemName(item).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || id.contains(query) || name.contains(query)) {
                results.add(item);
            }
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        // keep the search and count boxes, rebuild the list rows
        clearWidgets();
        addRenderableWidget(searchBox);
        addRenderableWidget(countBox);
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> addSelected())
                .bounds(this.width / 2 - 52, this.height - 60, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new ChecklistScreen(fileName))
        ).bounds(this.width / 2 + 16, this.height - 60, 60, 20).build());

        int start = page * PAGE_SIZE;
        int end = Math.min(results.size(), start + PAGE_SIZE);
        int y = 60;
        for (int i = start; i < end; i++) {
            Item item = results.get(i);
            String label = FarmListClient.itemName(item);
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selected = item;
                FarmListClient.message("Selected " + FarmListClient.itemName(item) + ". Set a count and press Add.");
            }).bounds(this.width / 2 - 120, y, 240, 20).build());
            y += ROW_HEIGHT;
        }

        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> { page--; rebuildButtons(); })
                    .bounds(this.width / 2 - 120, this.height - 90, 70, 20).build());
        }
        if (end < results.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), b -> { page++; rebuildButtons(); })
                    .bounds(this.width / 2 + 50, this.height - 90, 70, 20).build());
        }
    }

    private void addSelected() {
        if (selected == null) {
            FarmListClient.message("Pick an item from the list first.");
            return;
        }
        int count;
        try {
            count = Integer.parseInt(countBox.getValue().trim());
        } catch (NumberFormatException e) {
            FarmListClient.message("Count must be a number.");
            return;
        }
        if (count < 1) {
            FarmListClient.message("Count must be at least 1.");
            return;
        }
        FarmListClient.appendRow(fileName, FarmListClient.itemId(selected), count);
        this.minecraft.gui.setScreen(new ChecklistScreen(fileName));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, Component.literal("Search items"), this.width / 2, 16, 0xFFFFFFFF);
        g.centeredText(this.font, Component.literal("Count"), this.width / 2 - 90, this.height - 72, 0xFFAAAAAA);
        if (!FarmListClient.status.isEmpty()) {
            g.centeredText(this.font, Component.literal(FarmListClient.status), this.width / 2, this.height - 14, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
