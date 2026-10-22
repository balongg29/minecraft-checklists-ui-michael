package com.farmlist;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Saved state for one checklist, in .minecraft/farmlist/progress/<name>.properties.
 * Keys are item IDs (value "true"/"false" for checked) or "choice.<row>" (value = chosen block ID).
 * Saved on this computer only.
 */
public final class Progress {

    static final Path PROGRESS_DIR =
            FarmListClient.TRANSCRIPTION_DIR.getParent().resolve("progress");

    private final Path file;
    private final Map<String, String> values = new LinkedHashMap<>();

    public Progress(String checklistName) {
        this.file = PROGRESS_DIR.resolve(checklistName + ".properties");
        load();
    }

    public boolean isChecked(String rowId) {
        return Boolean.parseBoolean(values.getOrDefault(rowId, "false"));
    }

    public boolean isComplete() {
        return isChecked("_complete");
    }

    public void setComplete(boolean value) {
        set("_complete", value);
    }

    public void set(String rowId, boolean value) {
        values.put(rowId, Boolean.toString(value));
        save();
    }

    /** The block chosen for a building_block row, or null if none chosen yet. */
    public String getChoice(String rowId) {
        return values.get("choice." + rowId);
    }

    public void setChoice(String rowId, String blockId) {
        values.put("choice." + rowId, blockId);
        save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ignored) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            values.put(key, props.getProperty(key));
        }
    }

    private void save() {
        Properties props = new Properties();
        values.forEach(props::setProperty);
        try {
            Files.createDirectories(PROGRESS_DIR);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Farm Checklist progress");
            }
        } catch (IOException e) {
            FarmListClient.message("Could not save progress: " + e.getMessage());
        }
    }
}
