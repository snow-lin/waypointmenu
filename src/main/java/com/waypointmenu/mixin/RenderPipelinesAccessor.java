//? if <26.2 {
package com.waypointmenu.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.1 only: exposes the private {@code GLOBALS_SNIPPET} and
 * {@code MATRICES_PROJECTION_SNIPPET} so the see-through highlight pipeline can
 * carry the same bind-group layouts as vanilla's position_color shaders. 26.2
 * replaces these snippets with the public {@code BindGroupLayouts} constants, so
 * this mixin is absent there.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("GLOBALS_SNIPPET")
    static RenderPipeline.Snippet getGlobalsSnippet() {
        throw new AssertionError();
    }

    @Accessor("MATRICES_PROJECTION_SNIPPET")
    static RenderPipeline.Snippet getMatricesProjectionSnippet() {
        throw new AssertionError();
    }
}
//?}
