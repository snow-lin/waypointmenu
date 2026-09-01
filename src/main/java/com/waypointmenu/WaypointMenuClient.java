package com.waypointmenu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.waypointmenu.command.CommandSetExecutor;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.render.WaypointRenderer;
import com.waypointmenu.screen.WaypointListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint: drives the command-set queue and wires up world rendering.
 */
public class WaypointMenuClient implements ClientModInitializer {
    private static boolean comboWasHeld = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Reload waypoints whenever the player enters a different world.
            WaypointManager.getInstance().checkWorldChange();

            // Open/close the list when the configured key combination is pressed.
            if (client.player != null && comboPressed()) {
                if (ClientCompat.currentScreen(client) instanceof WaypointListScreen) {
                    ClientCompat.setScreen(client, null);
                } else {
                    ClientCompat.setScreen(client, new WaypointListScreen());
                }
            }
            // Advance the command-set queue (handles #sleep delays between commands).
            CommandSetExecutor.tick();
        });

        // In-world markers plus the HUD pass for far-away labels.
        WaypointRenderer.register();
    }

    /** Fires once when every key in the configured combination becomes held. */
    private static boolean comboPressed() {
        int[] combo = WaypointConfig.get().keyCombo;
        if (combo == null || combo.length == 0) {
            return false;
        }
        boolean held = allKeysHeld(combo);
        boolean edge = held && !comboWasHeld;
        comboWasHeld = held;
        return edge;
    }

    private static boolean allKeysHeld(int[] keys) {
        Window window = Minecraft.getInstance().getWindow();
        for (int key : keys) {
            if (key > 0 && !InputConstants.isKeyDown(window, key)) {
                return false;
            }
        }
        return true;
    }
}
