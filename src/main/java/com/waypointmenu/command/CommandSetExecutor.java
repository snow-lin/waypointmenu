package com.waypointmenu.command;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Runs a waypoint's command set in order.
 *
 * <p>Semantics follow the reference project (quick-menu): a line starting with
 * {@code /} is sent as a command (slash stripped); any other line is sent as a
 * chat message. A line of the form {@code #sleep <ticks>} (or {@code #delay
 * <ticks>}; extra leading {@code #}s are ignored) pauses the queue for that
 * many game ticks before the next line.</p>
 */
public class CommandSetExecutor {
    private static final ArrayDeque<String> pending = new ArrayDeque<>();
    private static int delayTicks = 0;

    /** Clears any in-flight queue and starts executing {@code commands}. */
    public static void execute(List<String> commands) {
        pending.clear();
        delayTicks = 0;
        if (commands != null) {
            pending.addAll(commands);
        }
    }

    public static boolean isRunning() {
        return !pending.isEmpty() || delayTicks > 0;
    }

    /** Called once per client tick; advances the queue. */
    public static void tick() {
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }
        if (pending.isEmpty()) {
            return;
        }

        String raw = pending.poll();
        if (raw == null) {
            return;
        }
        if (isSleepDirective(raw)) {
            // A sleep directive pauses the queue: set the delay and wait it out
            // before running the next line. Do NOT run anything else this tick.
            delayTicks = parseSleepTicks(raw);
            return;
        }
        run(raw);
    }

    private static boolean isSleepDirective(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }
        // Tolerate any number of leading '#' so "#sleep" and "##sleep" both work.
        int i = 0;
        while (i < trimmed.length() && trimmed.charAt(i) == '#') {
            i++;
        }
        String rest = trimmed.substring(i);
        return rest.startsWith("sleep") || rest.startsWith("delay");
    }

    private static int parseSleepTicks(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                return Math.max(0, Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // fall through to 0
            }
        }
        return 0;
    }

    private static void run(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null || client.player == null) {
            return;
        }

        String cmd = command.trim();
        if (cmd.startsWith("/")) {
            handler.sendChatCommand(cmd.substring(1));
        } else {
            if (cmd.length() > 256) {
                cmd = cmd.substring(0, 256);
            }
            handler.sendChatMessage(cmd);
        }
    }
}
