package net.minecraft.client.render;

//? if >=1.21.5 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}

/**
 * Builds the custom see-through highlight layer.
 *
 * <p>This helper lives in {@code net.minecraft.client.render} so it can reach the
 * package-private {@code RenderLayer} factories and {@code MultiPhaseParameters}
 * that are not accessible from the mod's own package.</p>
 *
 * <p>Before 1.21.5 it hand-rolls a classic {@code MultiPhase} layer with the
 * position-color shader program. From 1.21.5 to 1.21.10 it wraps a
 * caller-supplied {@code RenderPipeline} in a {@code MultiPhase} layer (the 1.21.5
 * render refactor moved program/transparency/depth/cull state into
 * {@code RenderPipeline}s). From 1.21.11 {@code RenderSetup} replaces
 * {@code MultiPhase}, so this class is no longer used for that path.</p>
 */
public final class WaypointMenuHighlightLayer {
    private WaypointMenuHighlightLayer() {
    }

    //? if <1.21.5 {
    public static RenderLayer create() {
        RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                //? if >=1.21.2 {
                .program(RenderPhase.POSITION_COLOR_PROGRAM)
                //?} else {
                .program(RenderPhase.COLOR_PROGRAM)
                //?}
                .transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
                .depthTest(RenderPhase.ALWAYS_DEPTH_TEST)
                .cull(RenderPhase.DISABLE_CULLING)
                .writeMaskState(RenderPhase.COLOR_MASK)
                .build(false);
        return RenderLayer.of("waypointmenu_highlight_beam", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 256, params);
    }
    //?}

    //? if >=1.21.5 <1.21.11 {
    /**
     * Wraps a ready-made position-color {@link RenderPipeline} (see-through:
     * no depth test, no cull) in a translucent {@code MultiPhase} layer. The
     * pipeline already carries the shader/blend/depth state; the
     * {@code MultiPhaseParameters} here only supply the default no-texture
     * phases, matching what {@code RenderSetup.builder(pipeline).translucent()}
     * would produce on 1.21.11.
     */
    public static RenderLayer createFromPipeline(RenderPipeline pipeline) {
        RenderLayer.MultiPhaseParameters params = RenderLayer.MultiPhaseParameters.builder()
                .texture(RenderPhase.NO_TEXTURE)
                .lightmap(RenderPhase.DISABLE_LIGHTMAP)
                .overlay(RenderPhase.DISABLE_OVERLAY_COLOR)
                .layering(RenderPhase.NO_LAYERING)
                .target(RenderPhase.MAIN_TARGET)
                .texturing(RenderPhase.DEFAULT_TEXTURING)
                .build(false);
        return RenderLayer.MultiPhase.of("waypointmenu_highlight_beam", 256, false, true, pipeline, params);
    }
    //?}
}
