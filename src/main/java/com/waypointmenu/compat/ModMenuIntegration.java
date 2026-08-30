package com.waypointmenu.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.waypointmenu.screen.WaypointConfigScreen;

/**
 * Mod Menu entrypoint: exposes this mod's config screen so it can be opened
 * straight from the Mod Menu list.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WaypointConfigScreen::new;
    }
}
