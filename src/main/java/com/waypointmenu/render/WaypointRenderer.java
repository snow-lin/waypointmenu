package com.waypointmenu.render;

import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.mixin.RenderLayerInvoker;
import com.waypointmenu.mixin.RenderPipelinesAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Draws an in-world marker for every waypoint whose highlight is toggled on.
 *
 * <p>The marker is a translucent, camera-facing (billboarded) diamond floating
 * above the waypoint, rendered without depth testing so it is visible through
 * terrain. The name/coordinate label is likewise see-through.</p>
 *
 * <p>Uses Fabric's {@link WorldRenderEvents#BEFORE_TRANSLUCENT} hook. Geometry and
 * text are submitted through the world renderer's
 * {@link WorldRenderContext#commandQueue()} so they are drawn in the correct
 * order with the rest of the translucent pass.</p>
 */
public class WaypointRenderer {

    /**
     * A translucent POSITION_COLOR layer with depth testing disabled, built once
     * from vanilla's own POSITION_COLOR snippet.
     */
    private static final RenderLayer HIGHLIGHT_LAYER = createHighlightLayer();

    /**
     * Distance (in blocks) at which the label uses its base world-space scale.
     * Beyond this the billboard scales up linearly with distance, so the text
     * keeps a constant on-screen size and stays readable far away.
     */
    private static final double LABEL_REFERENCE_DISTANCE = 8.0;

    private static RenderLayer createHighlightLayer() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccessor.getPositionColorSnippet())
                .withLocation(Identifier.of("waypointmenu", "highlight_beam"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build();
        return RenderLayerInvoker.invoke("waypointmenu_highlight_beam", RenderSetup.builder(pipeline).translucent().build());
    }

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WaypointRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        String playerDimension = client.world.getRegistryKey().getValue().toString();
        for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
            if (!WaypointManager.getInstance().isHighlighted(wp)) {
                continue;
            }
            if (!wp.dimension.equals(playerDimension)) {
                continue;
            }
            renderWaypoint(context, client, wp);
        }
    }

    private static void renderWaypoint(WorldRenderContext context, MinecraftClient client, Waypoint wp) {
        MatrixStack matrices = context.matrices();
        Vec3d cam = client.gameRenderer.getCamera().getCameraPos();

        // Diamond marker floating a couple of blocks above the waypoint.
        double cx = wp.x + 0.5;
        double cz = wp.z + 0.5;
        float markerW = 0.9f;
        float markerH = 1.3f;
        double markerY = wp.y + 2.0;

        int c = wp.color;
        float r = ((c >> 16) & 0xFF) / 255.0f;
        float g = ((c >> 8) & 0xFF) / 255.0f;
        float b = (c & 0xFF) / 255.0f;
        float a = WaypointConfig.get().highlightOpacity;

        // Billboard the diamond: translate to the marker, then rotate to face the
        // camera so it looks identical from every angle.
        matrices.push();
        matrices.translate(cx - cam.x, markerY - cam.y, cz - cam.z);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());

        context.commandQueue().submitCustom(matrices, HIGHLIGHT_LAYER, (entry, vc) ->
                drawFlatDiamond(entry, vc, markerW, markerH, r, g, b, a));

        matrices.pop();

        if (WaypointConfig.get().showLabel) {
            renderLabel(context, client, wp, cx, wp.y + 1.5, cz);
        }
    }

    private static void renderLabel(WorldRenderContext context, MinecraftClient client, Waypoint wp, double cx, double cy, double cz) {
        Camera camera = client.gameRenderer.getCamera();
        double dx = cx - camera.getCameraPos().x;
        double dy = cy - camera.getCameraPos().y;
        double dz = cz - camera.getCameraPos().z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > WaypointConfig.get().labelDistance) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
        String label = String.format("%s  %.0fm", wp.name, distance);

        MatrixStack matrices = context.matrices();

        matrices.push();
        matrices.translate(cx - camera.getCameraPos().x, cy + 2.5 - camera.getCameraPos().y, cz - camera.getCameraPos().z);
        // Billboard: rotate to face the camera, then flip Y like name tags do
        // (X stays positive — a negative X scale inverts the winding order and
        // gets the glyphs back-face culled, leaving no text visible).
        matrices.multiply(camera.getRotation());
        float scale = 0.04f * (float) Math.max(1.0, distance / LABEL_REFERENCE_DISTANCE);
        matrices.scale(scale, -scale, scale);

        float x = -textRenderer.getWidth(label) / 2.0f;
        // outlineColor must be 0: any non-zero value routes through drawWithOutline,
        // which forces TextLayerType.NORMAL (depth-tested) and hides the label behind
        // walls. 0 keeps us on the draw() path so the SEE_THROUGH layer applies.
        context.commandQueue().submitText(
                matrices,
                x, 0.0f,
                Text.literal(label).asOrderedText(),
                false,
                TextRenderer.TextLayerType.SEE_THROUGH,
                LightmapTextureManager.pack(15, 15),
                wp.color,
                0x44000000,
                0
        );

        matrices.pop();
    }

    /**
     * A flat, camera-facing diamond (rhombus): a single quad billboarded so it
     * appears the same from every viewing angle.
     */
    private static void drawFlatDiamond(MatrixStack.Entry m, VertexConsumer vc,
                                        float w, float h, float r, float g, float b, float a) {
        vc.vertex(m, 0, h, 0).color(r, g, b, a);   // top
        vc.vertex(m, w, 0, 0).color(r, g, b, a);   // right
        vc.vertex(m, 0, -h, 0).color(r, g, b, a);  // bottom
        vc.vertex(m, -w, 0, 0).color(r, g, b, a);  // left
    }
}
