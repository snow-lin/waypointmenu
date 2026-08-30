package com.waypointmenu.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A recorded location with an attached command set.
 *
 * <p>Fields are public so that Gson can (de)serialize them without reflection
 * configuration. {@link #id} is a stable identifier used to track highlight
 * state; {@link #commands} is the command set carried by this waypoint.</p>
 */
public class Waypoint {
    /** Default highlight color (ARGB, 0xFF00E6C0 = teal). */
    public static final int DEFAULT_COLOR = 0xFF00E6C0;

    public String id = "";
    public String name = "";
    public String description = "";
    public String dimension = "minecraft:overworld";
    /** Highlight color as ARGB (0xFFRRGGBB). */
    public int color = DEFAULT_COLOR;
    public int x;
    public int y;
    public int z;
    public List<String> commands = new ArrayList<>();

    public Waypoint() {
    }

    public Waypoint(String id, String name, String dimension, int x, int y, int z, List<String> commands) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
    }
}
