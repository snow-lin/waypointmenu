package com.waypointmenu.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the list of recorded {@link Waypoint}s and the transient set of
 * highlighted waypoints, and persists everything to JSON in the game's
 * config directory.
 *
 * <p>Each world gets its own file (keyed by the singleplayer world name or the
 * server address), so waypoints are not shared between worlds.</p>
 */
public class WaypointManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("waypointmenu");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static WaypointManager instance;

    private final List<Waypoint> waypoints = new ArrayList<>();
    private final Set<String> highlighted = new HashSet<>();

    private String worldKey = "";
    private Path file;

    private WaypointManager() {
        checkWorldChange();
    }

    public static WaypointManager getInstance() {
        if (instance == null) {
            instance = new WaypointManager();
        }
        return instance;
    }

    /**
     * Reloads the waypoint set when the player enters a different world.
     * Cheap enough to call every client tick.
     */
    public void checkWorldChange() {
        String key = currentWorldKey();
        if (key.equals(worldKey)) {
            return;
        }
        worldKey = key;
        file = FabricLoader.getInstance().getConfigDir()
                .resolve("waypointmenu")
                .resolve("waypoints_" + key + ".json");
        waypoints.clear();
        highlighted.clear();
        load();
    }

    private static String currentWorldKey() {
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server != null) {
            return "sp_" + sanitize(server.getWorldData().getLevelName());
        }
        ServerData info = client.getCurrentServer();
        if (info != null && info.ip != null && !info.ip.isEmpty()) {
            return "mp_" + sanitize(info.ip);
        }
        return "world";
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public List<Waypoint> getWaypoints() {
        return waypoints;
    }

    public Waypoint addWaypoint(String name, String dimension, int x, int y, int z, List<String> commands) {
        Waypoint w = new Waypoint(
                UUID.randomUUID().toString(),
                name,
                dimension,
                x, y, z,
                commands
        );
        waypoints.add(w);
        save();
        return w;
    }

    public void removeWaypoint(Waypoint w) {
        if (waypoints.remove(w)) {
            highlighted.remove(w.id);
            save();
        }
    }

    /**
     * Moves a waypoint to {@code toIndex} (the pre-removal full-list index it
     * should end up at), used by drag-to-reorder.
     */
    public void moveWaypoint(Waypoint w, int toIndex) {
        int from = waypoints.indexOf(w);
        if (from < 0 || from == toIndex) {
            return;
        }
        waypoints.remove(from);
        int insertAt = (toIndex > from) ? toIndex - 1 : toIndex;
        insertAt = Math.max(0, Math.min(insertAt, waypoints.size()));
        waypoints.add(insertAt, w);
        save();
    }

    /** Marks the manager dirty so it is written back to disk. */
    public void markDirty() {
        save();
    }

    public boolean isHighlighted(Waypoint w) {
        return highlighted.contains(w.id);
    }

    /** Toggles highlight state for a waypoint. Returns the new state (true = on). */
    public boolean toggleHighlight(Waypoint w) {
        boolean nowOn = !highlighted.remove(w.id);
        if (nowOn) {
            highlighted.add(w.id);
        }
        return nowOn;
    }

    public void clearHighlights() {
        highlighted.clear();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(new SaveData(waypoints)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save waypoints to {}", file, e);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            SaveData data = GSON.fromJson(json, SaveData.class);
            if (data == null || data.waypoints == null) {
                return;
            }
            waypoints.clear();
            for (Waypoint w : data.waypoints) {
                if (w == null) {
                    continue;
                }
                if (w.id == null || w.id.isEmpty()) {
                    w.id = UUID.randomUUID().toString();
                }
                if (w.commands == null) {
                    w.commands = new ArrayList<>();
                }
                if (w.name == null) {
                    w.name = "";
                }
                if (w.description == null) {
                    w.description = "";
                }
                if (w.dimension == null) {
                    w.dimension = "minecraft:overworld";
                }
                if (w.color == 0) {
                    w.color = Waypoint.DEFAULT_COLOR;
                }
                waypoints.add(w);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load waypoints from {}", file, e);
        }
    }

    private static class SaveData {
        List<Waypoint> waypoints;

        SaveData(List<Waypoint> waypoints) {
            this.waypoints = waypoints;
        }
    }
}
