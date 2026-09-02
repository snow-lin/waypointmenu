package com.waypointmenu.render;

//? if >=26.2 {
import com.mojang.blaze3d.PrimitiveTopology;
//?}
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//? if <26.2 {
import com.mojang.blaze3d.vertex.VertexFormat;
//?}
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.waypointmenu.ClientCompat;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.mixin.RenderTypeInvoker;
//? if <26.2 {
import com.waypointmenu.mixin.RenderPipelinesAccessor;
//?}
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
//? if >=26.2 {
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.2 {
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
//?}
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws an in-world marker for every waypoint whose highlight is toggled on.
 *
 * <p>The marker is a translucent, camera-facing (billboarded) diamond floating
 * above the waypoint, rendered without depth testing so it is visible through
 * terrain. The name/distance label is likewise see-through. Waypoints beyond the
 * engine's far clipping plane are handed off to a screen-space HUD pass that
 * draws only the label text.</p>
 *
 * <p>26.x renders through the frame graph: in-world geometry is submitted during
 * {@link LevelRenderEvents#END_MAIN} via {@link LevelRenderContext#submitNodeCollector()},
 * and the far-label fallback is a {@code HudElement} registered last on the HUD
 * layer stack.</p>
 */
public class WaypointRenderer {

    /**
     * A translucent POSITION_COLOR render type with depth testing disabled, built
     * once. The see-through effect comes from the ALWAYS_PASS depth test and the
     * TRANSLUCENT blend function; culling stays off so the flat diamond is visible
     * from both sides.
     */
    private static final RenderType HIGHLIGHT_TYPE = createHighlightType();

    private static final Logger LOGGER = LoggerFactory.getLogger("waypointmenu");
    private static int frameCounter = 0;
    private static int hudDiagCounter = 0;

    private static RenderPipeline createHighlightPipeline() {
        //? if >=26.2 {
        return RenderPipeline.builder(
                RenderPipeline.builder()
                        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                        .buildSnippet()
        )
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .withLocation(Identifier.fromNamespaceAndPath("waypointmenu", "highlight"))
                .build();
        //?} else {
        // 26.1 has no BindGroupLayouts / withVertexBinding / withPrimitiveTopology:
        // the bind-group layouts come from the GLOBALS + MATRICES_PROJECTION
        // snippets, and the vertex format + QUADS topology are set together.
        return RenderPipeline.builder(
                RenderPipelinesAccessor.getGlobalsSnippet(),
                RenderPipelinesAccessor.getMatricesProjectionSnippet()
        )
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .withLocation(Identifier.fromNamespaceAndPath("waypointmenu", "highlight"))
                .build();
        //?}
    }

    private static RenderType createHighlightType() {
        RenderSetup setup = RenderSetup.builder(createHighlightPipeline()).createRenderSetup();
        return RenderTypeInvoker.invoke("waypointmenu_highlight_beam", setup);
    }

    /**
     * Registers the in-world marker hook after the main pass so the see-through
     * marker composites on top of water instead of being tinted or hidden by it,
     * plus the HUD pass that draws far-away waypoint labels in screen space.
     */
    public static void register() {
        //? if >=26.2 {
        // 26.2 can submit to SubmitRenderPhases.ALWAYS_ON_TOP, which is rendered in its
        // own pass after the water-mask, so END_MAIN is fine (the node is not drawn by
        // the main pass).
        LevelRenderEvents.END_MAIN.register(WaypointRenderer::render);
        //?} else {
        // 26.1 has no submit phases: submitText/submitCustomGeometry always write into
        // the main pass's submit collection, which is rendered during the
        // translucent-features pass — BEFORE the translucent-terrain (water) pass that
        // tints and hides the see-through marker. To draw on top of water we render
        // immediately (not deferred) at AFTER_TRANSLUCENT_TERRAIN, which fires after
        // water but before the buffer source is flushed, so the marker composites over
        // water in the same frame with the correct camera.
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(WaypointRenderer::render);
        //?}
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("waypointmenu", "far_labels"),
                WaypointRenderer::renderFarLabels
        );
    }

    /**
     * 1.0 up close, then grows linearly with distance past the configured
     * fixed-size distance so the rendered element keeps a constant on-screen
     * size instead of shrinking into the distance. The label always uses this;
     * the diamond applies it only when its own distance-scaling toggle is on.
     */
    private static float distanceScale(double distance) {
        return (float) Math.max(1.0, distance / WaypointConfig.get().textFixedSizeDistance);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        String playerDimension = client.level.dimension().identifier().toString();
        int total = WaypointManager.getInstance().getWaypoints().size();
        int highlighted = 0;
        int matched = 0;
        Waypoint first = null;

        for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
            if (!WaypointManager.getInstance().isHighlighted(wp)) {
                continue;
            }
            highlighted++;
            if (!wp.dimension.equals(playerDimension)) {
                continue;
            }
            matched++;
            if (first == null) {
                first = wp;
            }
            renderWaypoint(context, client, wp);
        }

        frameCounter++;
        boolean detail = (frameCounter == 1) || (frameCounter % 200 == 0);
        if (detail && LOGGER.isInfoEnabled()) {
            Vec3 cam = context.levelState().cameraRenderState.pos;
            String firstInfo = first == null ? "none"
                    : first.name + "@" + first.x + "," + first.y + "," + first.z;
            LOGGER.info(
                    "[waypointmenu] frame={} dim={} total={} highlighted={} matched={} cam={} first={}",
                    frameCounter, playerDimension, total, highlighted, matched,
                    String.format("%.1f,%.1f,%.1f", cam.x, cam.y, cam.z), firstInfo);
        }
    }

    private static void renderWaypoint(LevelRenderContext context, Minecraft client, Waypoint wp) {
        PoseStack matrices = context.poseStack();
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cam = camera.pos;

        // Diamond marker floating a couple of blocks above the waypoint.
        double cx = wp.x + 0.5;
        double cz = wp.z + 0.5;
        double markerY = wp.y + 2.0;

        double mdx = cx - cam.x;
        double mdy = markerY - cam.y;
        double mdz = cz - cam.z;
        double distance = Math.sqrt(mdx * mdx + mdy * mdy + mdz * mdz);

        // Beyond the far clipping plane the engine drops our 3D geometry, so hand
        // the waypoint off to the screen-space HUD pass (label text only) instead.
        if (distance > farThreshold()) {
            return;
        }

        // The diamond is culled entirely past the configured render distance,
        // leaving only the label. When the diamond distance-scaling toggle is on
        // it also keeps a constant on-screen size (like the label) past the
        // fixed-size distance.
        if (distance <= WaypointConfig.get().diamondRenderDistance) {
            float distScale = WaypointConfig.get().diamondScaleWithDistance
                    ? distanceScale(distance)
                    : 1.0f;
            float markerW = 0.9f * distScale;
            float markerH = 1.3f * distScale;

            int c = wp.color;
            float r = ((c >> 16) & 0xFF) / 255.0f;
            float g = ((c >> 8) & 0xFF) / 255.0f;
            float b = (c & 0xFF) / 255.0f;
            float a = WaypointConfig.get().highlightOpacity;

            // Billboard the diamond: translate to the marker, then rotate to face
            // the camera so it looks identical from every angle.
            matrices.pushPose();
            matrices.translate(cx - cam.x, markerY - cam.y, cz - cam.z);
            matrices.mulPose(camera.orientation);
            //? if >=26.2 {
            // ALWAYS_ON_TOP runs after the water-mask pass, so the see-through marker
            // is not tinted or hidden by water. 26.1 has no phase system and keeps
            // the plain submitCustomGeometry call in the else branch below.
            context.submitNodeCollector().submitCustom(
                    SubmitRenderPhases.ALWAYS_ON_TOP,
                    new CustomFeatureRenderer.Submit(matrices.last().copy(), HIGHLIGHT_TYPE, (pose, vc) ->
                            drawFlatDiamond(pose, vc, markerW, markerH, r, g, b, a)));
            //?} else {
            // 26.1 has no submit phases: draw straight into the frame's buffer source
            // so it is flushed after the translucent-terrain (water) pass (see
            // register()). This is exactly what the deferred submitCustomGeometry path
            // does internally, just issued at a later render stage.
            VertexConsumer vc = context.bufferSource().getBuffer(HIGHLIGHT_TYPE);
            drawFlatDiamond(matrices.last(), vc, markerW, markerH, r, g, b, a);
            //?}
            matrices.popPose();
        }

        if (WaypointConfig.get().showLabel) {
            renderLabel(context, client, wp, cx, cz, cam);
        }
    }

    private static void renderLabel(LevelRenderContext context, Minecraft client, Waypoint wp, double cx, double cz, Vec3 cam) {
        // Measure from the diamond's centre so the label's anchor and on-screen
        // distance readout match the diamond exactly. There is no distance cull:
        // once highlighted, the label stays visible from any range.
        double markerY = wp.y + 2.0;
        double dx = cx - cam.x;
        double dy = markerY - cam.y;
        double dz = cz - cam.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float textScale = distanceScale(distance);
        Font font = client.font;
        String label = String.format("%s  %.0fm", wp.name, distance);

        PoseStack matrices = context.poseStack();
        CameraRenderState camera = context.levelState().cameraRenderState;

        matrices.pushPose();
        // The baseline gap scales with textScale too, so the on-screen separation
        // between the (fixed) diamond and the growing label stays constant.
        double labelY = markerY + 2.0 * textScale;
        matrices.translate(cx - cam.x, labelY - cam.y, cz - cam.z);
        // Billboard: rotate to face the camera, then scale exactly like vanilla name
        // tags do (scale(0.025, -0.025, 0.025)): mirror Y only.
        matrices.mulPose(camera.orientation);
        float scale = 0.04f * textScale;
        matrices.scale(scale, -scale, scale);

        float x = -font.width(label) / 2.0f;

        // outlineColor must be 0: any non-zero value routes through the outlined
        // text path, which forces the NORMAL (depth-tested) display mode and hides
        // the label behind walls. 0 keeps us on the SEE_THROUGH path.
        //? if >=26.2 {
        // SEE_THROUGH keeps the text depth-test-free; ALWAYS_ON_TOP places it after
        // the water-mask pass so the label is not tinted by water. 26.1 keeps the
        // plain submitText call (no phase system) in the else branch.
        context.submitNodeCollector().submitCustom(
                SubmitRenderPhases.ALWAYS_ON_TOP,
                new TextFeatureRenderer.Submit(
                        new Matrix4f(matrices.last().pose()),
                        x, 0.0f,
                        Component.literal(label).getVisualOrderText(),
                        false,
                        Font.DisplayMode.SEE_THROUGH,
                        0xF000F0, // fullbright packed light (block 15, sky 15)
                        wp.color,
                        0,
                        0
                ));
        //?} else {
        // 26.1 has no submit phases: draw the text immediately into the frame's buffer
        // source (flushed after the translucent-terrain/water pass) so the see-through
        // label is not tinted or hidden by water. This is the same Font.drawInBatch call
        // the deferred submitText path makes internally, issued at a later render stage.
        font.drawInBatch(
                Component.literal(label).getVisualOrderText(),
                x, 0.0f,
                wp.color,
                false,
                matrices.last().pose(),
                context.bufferSource(),
                Font.DisplayMode.SEE_THROUGH,
                0,        // backgroundColor
                0xF000F0  // fullbright packed light (block 15, sky 15)
        );
        //?}

        matrices.popPose();
    }

    /**
     * Screen-space fallback for waypoints beyond the engine's far clipping plane.
     * Projects each far waypoint's label anchor to pixel coordinates and draws only
     * the name/distance text (no diamond), so labels stay visible at any range
     * instead of vanishing at the far plane.
     */
    public static void renderFarLabels(GuiGraphicsExtractor gui, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.options == null) {
            return;
        }
        WaypointConfig config = WaypointConfig.get();
        if (!config.showLabel) {
            return;
        }

        String playerDimension = client.level.dimension().identifier().toString();
        double threshold = farThreshold();
        Camera camera = ClientCompat.mainCamera(client);
        Vec3 camPos = camera.position();
        // Build the world->camera rotation from yaw/pitch with one fixed formula
        // (yaw=0 faces +Z/south, positive pitch looks down). xRot()/yRot() are in
        // degrees, matching the old Camera#getPitch/getYaw.
        float yaw = (float) Math.toRadians(camera.yRot());
        float pitch = (float) Math.toRadians(camera.xRot());
        Quaternionf invRot = new Quaternionf()
                .rotationYXZ((float) Math.PI - yaw, -pitch, 0.0f)
                .conjugate();

        double fovDegrees = client.options.fov().get();
        float fovRadians = (float) Math.toRadians(fovDegrees);
        // The HUD draws in scaled window coordinates, not raw framebuffer pixels.
        int scaledWidth = client.getWindow().getGuiScaledWidth();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        Matrix4f proj = new Matrix4f().perspective(fovRadians, (float) scaledWidth / scaledHeight, 0.05f, 1_000_000f);

        // Match the on-screen size of the 3D label in its fixed-size regime.
        double focal = (scaledHeight / 2.0) / Math.tan(fovRadians / 2.0);
        float hudScale = (float) (0.04 * focal / config.textFixedSizeDistance);
        if (hudScale <= 0.0f) {
            return;
        }

        Font font = client.font;
        hudDiagCounter++;
        boolean hudLog = (hudDiagCounter <= 3) || (hudDiagCounter % 200 == 0);
        int farCount = 0;
        String firstFar = null;
        for (Waypoint wp : WaypointManager.getInstance().getWaypoints()) {
            if (!WaypointManager.getInstance().isHighlighted(wp)) {
                continue;
            }
            if (!wp.dimension.equals(playerDimension)) {
                continue;
            }

            double cx = wp.x + 0.5;
            double cz = wp.z + 0.5;
            double markerY = wp.y + 2.0;
            double dx = cx - camPos.x;
            double dy = markerY - camPos.y;
            double dz = cz - camPos.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance <= threshold) {
                continue; // rendered in world space
            }

            // Replicate the 3D label's floating anchor so the handoff is seamless.
            double labelY = markerY + 2.0 * distanceScale(distance);
            float[] screen = projectToScreen(proj, invRot, camPos, cx, labelY, cz, scaledWidth, scaledHeight);
            if (screen == null) {
                continue;
            }

            farCount++;
            if (firstFar == null) {
                firstFar = wp.name + "@" + String.format("%.0fm", distance)
                        + " -> (" + String.format("%.0f,%.0f", screen[0], screen[1]) + ")";
            }

            String label = String.format("%s  %.0fm", wp.name, distance);
            int tw = font.width(label);
            Matrix3x2fStack ms = gui.pose();
            ms.pushMatrix();
            ms.translate(screen[0], screen[1]);
            ms.scale(hudScale, hudScale);
            gui.text(font, label, -tw / 2, -font.lineHeight / 2, wp.color, false);
            ms.popMatrix();
        }

        if (hudLog && LOGGER.isInfoEnabled()) {
            LOGGER.info("[waypointmenu] far-label frame={} threshold={} scaled={}x{} hudScale={} farCount={} first={}",
                    hudDiagCounter, String.format("%.0f", threshold), scaledWidth, scaledHeight,
                    String.format("%.2f", hudScale), farCount, firstFar == null ? "none" : firstFar);
        }
    }

    /**
     * Projects a world-space point to pixel coordinates, or returns {@code null}
     * if it falls behind the camera or off-screen. Self-contained: uses only the
     * camera rotation and a JOML perspective matrix.
     */
    private static float[] projectToScreen(Matrix4f proj, Quaternionf invRot, Vec3 camPos,
                                           double wx, double wy, double wz, int fbWidth, int fbHeight) {
        Vector3f rel = new Vector3f((float) (wx - camPos.x), (float) (wy - camPos.y), (float) (wz - camPos.z));
        rel.rotate(invRot); // world -> camera space (camera looks down -Z)
        if (rel.z >= 0.0f) {
            return null; // behind the camera
        }
        Vector4f clip = new Vector4f(rel, 1.0f).mul(proj);
        float w = clip.w;
        if (w <= 0.0f) {
            return null;
        }
        float ndcX = clip.x / w;
        float ndcY = clip.y / w;
        if (ndcX < -1.0f || ndcX > 1.0f || ndcY < -1.0f || ndcY > 1.0f) {
            return null;
        }
        float sx = (ndcX * 0.5f + 0.5f) * fbWidth;
        float sy = (1.0f - (ndcY * 0.5f + 0.5f)) * fbHeight;
        return new float[]{sx, sy};
    }

    /**
     * Distance (in blocks) past which a waypoint switches from 3D world
     * rendering to the screen-space HUD label, two chunks beyond the nominal
     * render distance to avoid cutting the marker off at the fog edge.
     */
    private static double farThreshold() {
        double chunks = Minecraft.getInstance().options.renderDistance().get();
        return (chunks + 2.0) * 16.0;
    }

    /**
     * A flat, camera-facing diamond (rhombus): a single quad billboarded so it
     * appears the same from every viewing angle.
     */
    private static void drawFlatDiamond(PoseStack.Pose pose, VertexConsumer vc,
                                        float w, float h, float r, float g, float b, float a) {
        vc.addVertex(pose, 0, h, 0).setColor(r, g, b, a);   // top
        vc.addVertex(pose, w, 0, 0).setColor(r, g, b, a);   // right
        vc.addVertex(pose, 0, -h, 0).setColor(r, g, b, a);  // bottom
        vc.addVertex(pose, -w, 0, 0).setColor(r, g, b, a);  // left
    }
}
