package com.waypointmenu.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link RenderType#create(String, RenderSetup)}, which is package-private
 * in vanilla, so the mod can build a custom render type for its see-through
 * highlight beam.
 */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker("create")
    static RenderType invoke(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
