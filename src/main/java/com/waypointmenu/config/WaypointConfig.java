package com.waypointmenu.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side configuration, persisted as JSON in the game's config directory.
 * The highlight beam opacity, whether right-click teleport is enabled and
 * whether the floating label is shown at all are read from here at render time.
 */
public class WaypointConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointmenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static WaypointConfig instance;

    public float highlightOpacity = 0.35f;
    public boolean rightClickTeleport = false;
    public boolean showLabel = true;
    public double textFixedSizeDistance = 10.0;
    public double diamondRenderDistance = 128.0;
    public boolean diamondScaleWithDistance = true;
    public boolean crossDimensionTeleport = false;
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
                this.rightClickTeleport = data.rightClickTeleport;
                this.showLabel = data.showLabel;
                this.textFixedSizeDistance = clampTextFixedDistance(data.textFixedSizeDistance);
                this.diamondRenderDistance = clampDiamondRenderDistance(data.diamondRenderDistance);
                this.diamondScaleWithDistance = data.diamondScaleWithDistance;
                this.crossDimensionTeleport = data.crossDimensionTeleport;
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

    public static double clampTextFixedDistance(double v) {
        return Math.max(1.0, Math.min(128.0, v));
    }

    public static double clampDiamondRenderDistance(double v) {
        // Cap the diamond distance at the current world render distance
        // (view distance × 16 blocks) so it can never exceed how far the world
        // actually renders.
        double max = 1024.0;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            max = client.options.getViewDistance().getValue() * 16.0;
        }
        return Math.max(16.0, Math.min(max, v));
    }

    /** Plain serialization DTO (defaults mirror the field defaults above). */
    private static class Data {
        float highlightOpacity = 0.35f;
        boolean rightClickTeleport = false;
        boolean showLabel = true;
        double textFixedSizeDistance = 10.0;
        double diamondRenderDistance = 128.0;
        boolean diamondScaleWithDistance = true;
        boolean crossDimensionTeleport = false;
        int[] keyCombo = {GLFW.GLFW_KEY_G};
    }
}
