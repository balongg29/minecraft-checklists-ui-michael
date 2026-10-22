package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Asks for a name, then creates an empty checklist with that name. */
public class NameScreen extends Screen {

    private EditBox nameBox;

    public NameScreen() {
        super(Component.literal("New Checklist"));
    }

    @Override
    protected void init() {
        nameBox = new EditBox(this.font, this.width / 2 - 120, 60, 240, 20, Component.literal("Name"));
        nameBox.setMaxLength(60);
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);

        addRenderableWidget(Button.builder(Component.literal("Create"), b -> {
            String name = nameBox.getValue().trim();
            if (name.isEmpty()) {
                FarmListClient.message("Type a name first.");
                return;
            }
            String file = FarmListClient.saveNamedChecklist(name, "# " + name + "\n");
            if (file != null) this.minecraft.gui.setScreen(new ChecklistScreen(file));
        }).bounds(this.width / 2 - 120, 90, 116, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b ->
                this.minecraft.gui.setScreen(new PickerScreen())
        ).bounds(this.width / 2 + 4, 90, 116, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(this.font, Component.literal("Name your checklist"), this.width / 2, 40, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
