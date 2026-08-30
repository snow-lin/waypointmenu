package com.waypointmenu.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link RenderLayer#of(String, RenderSetup)}, which is package-private
 * in vanilla, so we can build a custom render layer for the see-through
 * highlight beam.
 */
@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
    @Invoker("of")
    static RenderLayer invoke(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
