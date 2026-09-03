package com.waypointmenu;

import com.waypointmenu.command.CommandSetExecutor;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.render.WaypointRenderer;
import com.waypointmenu.screen.WaypointListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;

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
            // Track the key edge every tick, but only act while in-game or with
            // our own list open so typing in chat/commands doesn't trigger it.
            boolean edge = comboPressed();
            if (client.player != null && edge && canToggleMenu(client)) {
                if (client.currentScreen instanceof WaypointListScreen) {
                    client.setScreen(null);
                } else {
                    client.setScreen(new WaypointListScreen());
                }
            }
            // Advance the command-set queue (handles #sleep delays between commands).
            CommandSetExecutor.tick();
        });

        WaypointRenderer.register();

        // Draw far-away waypoint labels in screen space (their 3D geometry is
        // clipped by the engine's far plane, so they fall back to the HUD pass).
        HudRenderCallback.EVENT.register((context, tickCounter) -> WaypointRenderer.renderFarLabels(context));
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

    /** True when the combo may act: no screen is capturing input, or our own list is open. */
    private static boolean canToggleMenu(MinecraftClient client) {
        return client.currentScreen == null || client.currentScreen instanceof WaypointListScreen;
    }

    private static boolean allKeysHeld(int[] keys) {
        Window window = MinecraftClient.getInstance().getWindow();
        for (int key : keys) {
            //? if >=1.21.9 {
            if (key > 0 && !InputUtil.isKeyPressed(window, key)) {
            //?} else {
            if (key > 0 && !InputUtil.isKeyPressed(window.getHandle(), key)) {
            //?}
                return false;
            }
        }
        return true;
    }
}
