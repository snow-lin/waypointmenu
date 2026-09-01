//? if >=1.21.11 {
package com.waypointmenu.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link RenderLayer#of(String, RenderSetup)}, which is package-private
 * in vanilla, so we can build a custom render layer for the see-through
 * highlight beam. Only present from 1.21.11 on: {@code RenderSetup} (and this
 * {@code of} overload) does not exist in 1.21.5/1.21.6, which still use the
 * classic {@code RenderLayer.MultiPhase} builder.
 */
@Mixin(RenderLayer.class)
public interface RenderLayerInvoker {
    @Invoker("of")
    static RenderLayer invoke(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
//?}
