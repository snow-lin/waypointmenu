package com.waypointmenu.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side configuration, persisted as JSON in the game's config directory.
 * The highlight beam opacity, the label draw distance and whether the floating
 * label is shown at all are read from here at render time.
 */
public class WaypointConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointmenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static WaypointConfig instance;

    public float highlightOpacity = 0.35f;
    public int labelDistance = 512;
    public boolean showLabel = true;
    public int[] keyCombo = {GLFW.GLFW_KEY_G};

    private transient Path file;

    private WaypointConfig() {
        this.file = FabricLoader.getInstance().getConfigDir()
                .resolve("waypointmenu")
                .resolve("config.json");
        load();
    }

    public static WaypointConfig get() {
        if (instance == null) {
            instance = new WaypointConfig();
        }
        return instance;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save config to {}", file, e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
            if (data != null) {
                this.highlightOpacity = clampOpacity(data.highlightOpacity);
                this.labelDistance = clampDistance(data.labelDistance);
                this.showLabel = data.showLabel;
                this.keyCombo = (data.keyCombo == null || data.keyCombo.length == 0)
                        ? new int[]{GLFW.GLFW_KEY_G}
                        : data.keyCombo;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load config from {}", file, e);
        }
    }

    public static float clampOpacity(float v) {
        return Math.max(0.05f, Math.min(1.0f, v));
    }

    public static int clampDistance(int v) {
        return Math.max(64, Math.min(1024, v));
    }

    /** Plain serialization DTO (defaults mirror the field defaults above). */
    private static class Data {
        float highlightOpacity = 0.35f;
        int labelDistance = 512;
        boolean showLabel = true;
        int[] keyCombo = {GLFW.GLFW_KEY_G};
    }
}
