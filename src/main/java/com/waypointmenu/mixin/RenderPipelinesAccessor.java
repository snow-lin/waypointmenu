package com.waypointmenu.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code POSITION_COLOR_SNIPPET} render pipeline snippet so
 * we can reuse it (blend translucent, no depth write, POSITION_COLOR quads) for
 * the highlight beam and only change the depth test.
 */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("POSITION_COLOR_SNIPPET")
    static RenderPipeline.Snippet getPositionColorSnippet() {
        throw new AssertionError();
    }
}
