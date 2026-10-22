package com.farmlist;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Client side: opens the screens, reads and writes checklist files, and scans the inventory. */
public class FarmListClient implements ClientModInitializer {

    static final Path TRANSCRIPTION_DIR =
            FabricLoader.getInstance().getGameDir().resolve("farmlist").resolve("transcriptions");

    static final String BUILDING_BLOCK = "building_block";

    /** Latest status line, shown on the screen. */
    static String status = "";

    private static List<String> blockOptions = null;

    /** Press this key anywhere in game to open your checklists. Rebind it in Controls. */
    static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.farmlist.open", InputConstants.Type.KEYSYM, InputConstants.KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmlist", "main"))));

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN_KEY.consumeClick()) {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new PickerScreen());
                }
            }
        });
    }

    /** Parses lines like "wheat_seeds 64", skipping titles, comments, and blank lines. */
    static Map<String, Integer> parseRequired(Path file) {
        Map<String, Integer> required = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length != 2) continue;
                try {
                    required.put(normalize(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return required;
    }

    /** Counts item IDs in the player's inventory. */
    static Map<String, Integer> countInventory() {
        Map<String, Integer> counts = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return counts;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /** Every block item in the game, sorted by name, minus stairs and slabs. */
    static List<String> blockOptions() {
        if (blockOptions == null) {
            List<String> ids = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof net.minecraft.world.item.BlockItem) {
                    String id = BuiltInRegistries.ITEM.getKey(item).getPath();
                    if (id.endsWith("_stairs") || id.endsWith("_slab")) continue;
                    ids.add(id);
                }
            }
            ids.sort(null);
            blockOptions = ids;
        }
        return blockOptions;
    }

    /** Every item in the game, for the catalog search. */
    static List<Item> allItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) items.add(item);
        return items;
    }

    static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    static String itemName(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    /** Item ID that actually gets counted for a row: the chosen block, or the row ID itself. */
    static String countedId(String rowId, Progress progress) {
        if (BUILDING_BLOCK.equals(rowId)) {
            String choice = progress.getChoice(rowId);
            return choice != null ? choice : rowId;
        }
        return rowId;
    }

    static String normalize(String id) {
        return id.replace("minecraft:", "").toLowerCase();
    }

    static List<String> listTranscriptions() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(TRANSCRIPTION_DIR)) return names;
        try (Stream<Path> s = Files.list(TRANSCRIPTION_DIR)) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException ignored) {
        }
        return names;
    }

    /** Copies a checklist's text to the clipboard so it can be pasted elsewhere. */
    static void copyToClipboard(String fileName) {
        try {
            String text = Files.readString(TRANSCRIPTION_DIR.resolve(fileName), StandardCharsets.UTF_8);
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            message("Copied " + fileName + " to the clipboard.");
        } catch (IOException e) {
            message("Could not read " + fileName);
        }
    }

    /**
     * Reads a transcription from the clipboard, names the file after its "# Title" line,
     * saves it into the transcriptions folder, and returns the saved file name (or null).
     */
    static String importFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            message("Clipboard is empty. Copy the checklist text first.");
            return null;
        }
        String title = null;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("#") && !t.toUpperCase().contains("NEEDS") && !t.toUpperCase().contains("GUESS")) {
                title = t.replaceFirst("^#+", "").trim();
                break;
            }
        }
        if (title == null || title.isEmpty()) {
            title = "Checklist " + System.currentTimeMillis();
        }
        return saveNamedChecklist(title, text);
    }

    /** Saves a checklist file with the given title as its name and first line. */
    static String saveNamedChecklist(String title, String text) {
        String name = title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".txt";
        try {
            Files.createDirectories(TRANSCRIPTION_DIR);
            Files.writeString(TRANSCRIPTION_DIR.resolve(name), text, StandardCharsets.UTF_8);
            message("Saved " + name + ".");
            return name;
        } catch (IOException e) {
            message("Could not save " + name + ": " + e.getMessage());
            return null;
        }
    }

    /** Appends one "item count" row to a checklist file. */
    static void appendRow(String fileName, String itemId, int count) {
        try {
            Files.writeString(TRANSCRIPTION_DIR.resolve(fileName),
                    "\n" + itemId + " " + count + "\n",
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            message("Added " + count + " " + itemId + ".");
        } catch (IOException e) {
            message("Could not add row: " + e.getMessage());
        }
    }

    /** Deletes a checklist file and its saved checkboxes. */
    static void deleteChecklist(String fileName) {
        try {
            Files.deleteIfExists(TRANSCRIPTION_DIR.resolve(fileName));
            String base = fileName.replace(".txt", "");
            Files.deleteIfExists(Progress.PROGRESS_DIR.resolve(base + ".properties"));
            message("Deleted " + fileName + ".");
        } catch (IOException e) {
            message("Could not delete " + fileName + ": " + e.getMessage());
        }
    }

    static void message(String text) {
        status = text;
    }
}
