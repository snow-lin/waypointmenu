package com.waypointmenu;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version bridges for 26.1 vs 26.2. 26.2 moved screen ownership off
 * {@link Minecraft} onto {@code Gui} ({@code gui.screen()} /
 * {@code gui.setScreen()}) and renamed {@code getMainCamera()} to
 * {@code mainCamera()}; 26.1 still uses the older {@code Minecraft.screen}
 * field, {@code Minecraft.setScreen()} and {@code getMainCamera()}.
 */
public final class ClientCompat {
    private ClientCompat() {
    }

    public static Screen currentScreen(Minecraft client) {
        //? if <26.2 {
        return client.screen;
        //?} else {
        return client.gui.screen();
        //?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if <26.2 {
        client.setScreen(screen);
        //?} else {
        client.gui.setScreen(screen);
        //?}
    }

    public static Camera mainCamera(Minecraft client) {
        //? if <26.2 {
        return client.gameRenderer.getMainCamera();
        //?} else {
        return client.gameRenderer.mainCamera();
        //?}
    }
}
