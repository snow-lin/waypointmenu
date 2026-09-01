package com.waypointmenu.render;

//? if >=1.21.10 {
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.waypointmenu.mixin.RenderPipelinesAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.render.WaypointMenuHighlightLayer;
import net.minecraft.util.Identifier;
//? if >=1.21.11 {
import com.waypointmenu.mixin.RenderLayerInvoker;
import net.minecraft.client.render.RenderSetup;
//?}
//?} elif >=1.21.5 <1.21.9 {
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.waypointmenu.mixin.RenderPipelinesAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.WaypointMenuHighlightLayer;
import net.minecraft.util.Identifier;
//?} elif <1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.WaypointMenuHighlightLayer;
import org.lwjgl.opengl.GL11;
//?}
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
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
 * terrain. The name/coordinate label is likewise see-through.</p>
 *
 * <p>Three rendering eras across the supported versions:</p>
 * <ul>
 *   <li>{@code >=1.21.10} hooks Fabric's {@code WorldRenderEvents#END_MAIN}
 *       (package {@code ...rendering.v1.world}) and submits geometry/text through
 *       the world renderer's command queue.</li>
 *   <li>{@code <1.21.9} hooks {@code WorldRenderEvents#AFTER_TRANSLUCENT} (package
 *       {@code ...rendering.v1}) and writes through the classic
 *       {@code VertexConsumerProvider}.</li>
 *   <li>{@code 1.21.9} ships rendering-v1 16.0.1, which removed the world render
 *       events entirely before they returned (under {@code ...rendering.v1.world})
 *       in 16.2.0 for 1.21.10. With no world-space hook there, in-world markers
 *       are unavailable and every waypoint degrades to the HUD label pass.</li>
 * </ul>
 */
public class WaypointRenderer {

    //? if !=1.21.9 {
    /**
     * A translucent POSITION_COLOR layer with depth testing disabled, built once.
     * New-era builds it from vanilla's own POSITION_COLOR snippet; old-era uses
     * a hand-rolled {@link RenderLayer.MultiPhase} layer.
     */
    private static final RenderLayer HIGHLIGHT_LAYER = createHighlightLayer();
    //?}

    private static final Logger LOGGER = LoggerFactory.getLogger("waypointmenu");
    private static int frameCounter = 0;
    private static int diagFrame = 0;
    private static int hudDiagCounter = 0;

    //? if !=1.21.9 {
    private static RenderLayer createHighlightLayer() {
        //? if >=1.21.11 {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccessor.getPositionColorSnippet())
                .withLocation(Identifier.of("waypointmenu", "highlight_beam"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build();
        return RenderLayerInvoker.invoke("waypointmenu_highlight_beam", RenderSetup.builder(pipeline).translucent().build());
        //?} elif >=1.21.5 {
        // 1.21.5..1.21.10 still use RenderLayer.MultiPhase (RenderSetup does not
        // exist until 1.21.11); the pipeline carries the shader/blend/depth state,
        // and the same-package helper wraps it in a translucent MultiPhase layer.
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccessor.getPositionColorSnippet())
                .withLocation(Identifier.of("waypointmenu", "highlight_beam"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .build();
        return WaypointMenuHighlightLayer.createFromPipeline(pipeline);
        //?} else {
        return WaypointMenuHighlightLayer.create();
        //?}
    }
    //?}

    /**
     * Registers the in-world marker hook after the translucent pass (water, ice)
     * so the see-through marker composites on top of water instead of being tinted
     * or hidden by it. 1.21.9 is the one gap: its Fabric API (rendering-v1 16.0.1)
     * dropped the world render events entirely before they returned under
     * {@code ...rendering.v1.world} in 16.2.0 (1.21.10), so on that version this
     * is a no-op and every highlighted waypoint is drawn by the HUD label pass.
     */
    public static void register() {
        //? if >=1.21.10 {
        WorldRenderEvents.END_MAIN.register(WaypointRenderer::render);
        //?} elif <1.21.9 {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaypointRenderer::render);
        //?} else {
        //?}
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

    //? if !=1.21.9 {
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        String playerDimension = client.world.getRegistryKey().getValue().toString();
        int total = WaypointManager.getInstance().getWaypoints().size();
        int highlighted = 0;
        int matched = 0;
        Waypoint first = null;

        //? if <1.21.5 {
        // Pre-1.21.5 flushes the diamond lazily: requesting the label's layer in
        // renderLabel -> textRenderer.draw -> Immediate.getBuffer draws the previous
        // (diamond) layer on the spot, inside the loop below — before the final flush.
        // Disable the depth test up front so BOTH the diamond and the label render
        // see-through, and restore it in the finally block after the final flush.
        if (diagFrame == 0) {
            LOGGER.info("[waypointmenu] depth-diag before-disable: test={}, func={}",
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        }
        RenderSystem.disableDepthTest();
        if (diagFrame == 0) {
            LOGGER.info("[waypointmenu] depth-diag after-disable: test={}, func={}",
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        }
        try {
        //?}
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

        //? if <1.21.5 {
        // Flush only the pending label now. At AFTER_ENTITIES vanilla has already drawn
        // the entities and reset the shared Immediate's current layer, so the only thing
        // left pending is our own diamond/text. Vanilla would normally flush that during
        // renderBlockEntities, but on 1.21.1/1.21.4 that drops the text's render state
        // and leaves the label invisible (the diamond is unaffected because it's already
        // flushed lazily inside the loop above, when the label requests its layer).
        // drawCurrentLayer() touches only our layer — vanilla's entities are already
        // drawn, so nothing is reordered.
        ((VertexConsumerProvider.Immediate) context.consumers()).drawCurrentLayer();
        } finally {
            RenderSystem.enableDepthTest();
            if (diagFrame == 0) {
                LOGGER.info("[waypointmenu] depth-diag after-enable: test={}, func={}",
                        GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
                diagFrame++;
            }
        }
        //?}

        frameCounter++;
        boolean detail = (frameCounter == 1) || (frameCounter % 200 == 0);
        if (detail && LOGGER.isInfoEnabled()) {
            Vec3d cam = cameraPos(client.gameRenderer.getCamera());
            String firstInfo = first == null ? "none"
                    : first.name + "@" + first.x + "," + first.y + "," + first.z;
            LOGGER.info(
                    "[waypointmenu] frame={} dim={} total={} highlighted={} matched={} cam={} first={}",
                    frameCounter, playerDimension, total, highlighted, matched,
                    String.format("%.1f,%.1f,%.1f", cam.x, cam.y, cam.z), firstInfo);
            //? if <1.21.5 {
            if (first != null) {
                logClipDiagnostic(context, client, first);
            }
            //?}
        }
    }

    private static void renderWaypoint(WorldRenderContext context, MinecraftClient client, Waypoint wp) {
        //? if >=1.21.10 {
        MatrixStack matrices = context.matrices();
        //?} else {
        MatrixStack matrices = context.matrixStack();
        //?}
        Vec3d cam = cameraPos(client.gameRenderer.getCamera());

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
            matrices.push();
            matrices.translate(cx - cam.x, markerY - cam.y, cz - cam.z);
            matrices.multiply(client.gameRenderer.getCamera().getRotation());

            //? if >=1.21.10 {
            context.commandQueue().submitCustom(matrices, HIGHLIGHT_LAYER, (entry, vc) ->
                    drawFlatDiamond(entry, vc, markerW, markerH, r, g, b, a));
            //?} else {
            VertexConsumer vc = context.consumers().getBuffer(HIGHLIGHT_LAYER);
            drawFlatDiamond(matrices.peek().getPositionMatrix(), vc, markerW, markerH, r, g, b, a);
            //?}

            matrices.pop();
        }

        if (WaypointConfig.get().showLabel) {
            renderLabel(context, client, wp, cx, cz);
        }
    }

    private static void renderLabel(WorldRenderContext context, MinecraftClient client, Waypoint wp, double cx, double cz) {
        Camera camera = client.gameRenderer.getCamera();
        // Measure from the diamond's centre so the label's anchor and on-screen
        // distance readout match the diamond exactly. There is no distance cull:
        // once highlighted, the label stays visible from any range.
        double markerY = wp.y + 2.0;
        double dx = cx - cameraPos(camera).x;
        double dy = markerY - cameraPos(camera).y;
        double dz = cz - cameraPos(camera).z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float textScale = distanceScale(distance);
        TextRenderer textRenderer = client.textRenderer;
        String label = String.format("%s  %.0fm", wp.name, distance);

        //? if >=1.21.10 {
        MatrixStack matrices = context.matrices();
        //?} else {
        MatrixStack matrices = context.matrixStack();
        //?}

        matrices.push();
        // The baseline gap scales with textScale too, so the on-screen separation
        // between the (fixed) diamond and the growing label stays constant.
        double labelY = markerY + 2.0 * textScale;
        matrices.translate(cx - cameraPos(camera).x, labelY - cameraPos(camera).y, cz - cameraPos(camera).z);
        // Billboard: rotate to face the camera, then scale exactly like vanilla name
        // tags do (scale(0.025, -0.025, 0.025)): mirror Y only. The see-through text
        // layer keeps culling ENABLED, so the transform's winding must match what the
        // glyphs expect.
        //   1.20.x: glyph winding needs the mirrored-X variant (scale(-s, -s, s)) to
        //           stay front-facing under culling.
        //   1.21.1+ (including 1.21.5/1.21.11): Mojang flipped the glyph winding at
        //           1.21.0; only the mirror-Y variant (scale(s, -s, s)) stays
        //           front-facing. 1.21.11's frame-graph SEE_THROUGH pipeline still
        //           defaults to cull=true (it never calls withCull(false)), so the
        //           old -X transform would be back-face culled — invisible label.
        matrices.multiply(camera.getRotation());
        float scale = 0.04f * textScale;
        //? if >=1.21 {
        matrices.scale(scale, -scale, scale);
        //?} else {
        matrices.scale(-scale, -scale, scale);
        //?}

        float x = -textRenderer.getWidth(label) / 2.0f;

        //? if >=1.21.10 {
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
                0,
                0
        );
        //?} else {
        // SEE_THROUGH keeps the label on the depth-test-disabled draw() path so it
        // stays visible through walls, matching the diamond above. Background color
        // is 0 (no name-tag style dimming box): a non-zero value like 0x44000000
        // paints a semi-opaque black rectangle ON TOP of the glyphs, leaving the
        // label ~73% opaque so the world behind it (sky/water/terrain) bleeds
        // through — the label's brightness then drifts as the camera moves.
        textRenderer.draw(
                Text.literal(label).asOrderedText(),
                x, 0.0f,
                wp.color,
                false,
                matrices.peek().getPositionMatrix(),
                context.consumers(),
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                LightmapTextureManager.pack(15, 15)
        );
        //?}

        matrices.pop();
    }
    //?}

    /**
     * Screen-space fallback for waypoints beyond the engine's far clipping plane.
     * Registered on the HUD pass: projects each far waypoint's label anchor to
     * pixel coordinates and draws only the name/distance text (no diamond), so
     * labels stay visible at any range instead of vanishing at the far plane.
     * On 1.21.9 (no world render events) this also draws every near waypoint,
     * since there is no in-world marker to render them.
     */
    public static void renderFarLabels(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options == null) {
            return;
        }
        WaypointConfig config = WaypointConfig.get();
        if (!config.showLabel) {
            return;
        }

        String playerDimension = client.world.getRegistryKey().getValue().toString();
        double threshold = farThreshold();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = cameraPos(camera);
        // The camera rotation quaternion's convention changed between versions
        // (1.20.x builds it as rotationYXZ(-yaw, pitch, 0), 1.21.x as
        // rotationYXZ(PI - yaw, -pitch, 0)), so reading getRotation() directly
        // made far labels drift with the mouse on 1.20.x. Build the world→camera
        // rotation from yaw/pitch with one fixed formula to stay consistent on
        // every version (yaw=0 faces +Z/south, positive pitch looks down).
        float yaw = (float) Math.toRadians(camera.getYaw());
        float pitch = (float) Math.toRadians(camera.getPitch());
        Quaternionf invRot = new Quaternionf()
                .rotationYXZ((float) Math.PI - yaw, -pitch, 0.0f)
                .conjugate();

        double fovDegrees = client.options.getFov().getValue();
        float fovRadians = (float) Math.toRadians(fovDegrees);
        // The HUD (and DrawContext) draw in scaled window coordinates, not raw
        // framebuffer pixels — projecting with the framebuffer size would place
        // every far label guiScale× off-screen on any scaled GUI.
        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        Matrix4f proj = new Matrix4f().perspective(fovRadians, (float) scaledWidth / scaledHeight, 0.05f, 1_000_000f);

        // Match the on-screen size of the 3D label in its fixed-size regime.
        double focal = (scaledHeight / 2.0) / Math.tan(fovRadians / 2.0);
        float hudScale = (float) (0.04 * focal / config.textFixedSizeDistance);
        if (hudScale <= 0.0f) {
            return;
        }

        TextRenderer textRenderer = client.textRenderer;
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
            //? if !=1.21.9 {
            if (distance <= threshold) {
                continue; // rendered in world space
            }
            //?}

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
            int tw = textRenderer.getWidth(label);
            //? if >=1.21.6 {
            // 1.21.6+ replaced the GUI's 3D MatrixStack with a 2D Matrix3x2fStack.
            org.joml.Matrix3x2fStack ms = context.getMatrices();
            ms.pushMatrix();
            ms.translate(screen[0], screen[1]);
            ms.scale(hudScale, hudScale);
            context.drawText(textRenderer, Text.literal(label), -tw / 2, -textRenderer.fontHeight / 2, wp.color, false);
            ms.popMatrix();
            //?} else {
            MatrixStack ms = context.getMatrices();
            ms.push();
            ms.translate(screen[0], screen[1], 0.0f);
            ms.scale(hudScale, hudScale, 1.0f);
            context.drawText(textRenderer, Text.literal(label), -tw / 2, -textRenderer.fontHeight / 2, wp.color, false);
            ms.pop();
            //?}
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
     * camera rotation and a JOML perspective matrix, so it behaves identically on
     * every MC version (the 3D path's matrices aren't exposed on 1.21.5+).
     */
    private static float[] projectToScreen(Matrix4f proj, Quaternionf invRot, Vec3d camPos,
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
     * rendering to the screen-space HUD label. The 3D marker is see-through and
     * keeps rendering a little past the world's fog (which ends at
     * {@code viewDistance * 16} blocks), so the handoff sits two chunks beyond
     * the nominal render distance to avoid cutting the marker off at the fog
     * edge.
     */
    private static double farThreshold() {
        double chunks = MinecraftClient.getInstance().options.getViewDistance().getValue();
        return (chunks + 2.0) * 16.0;
    }

    /**
     * Diagnoses why a waypoint marker may not be visible: rebuilds the exact
     * billboard transform and projects the diamond centre through the shader's
     * own matrices ({@code ProjMat * ModelViewMat * model}), logging the
     * resulting NDC. A centre inside {@code [-1,1]^3} with {@code w > 0} means
     * the geometry is on screen (pointing at a shader/state problem); anything
     * outside means the matrix/position is wrong. Old-era only.
     */
    //? if <1.21.5 {
    private static void logClipDiagnostic(WorldRenderContext context, MinecraftClient client, Waypoint wp) {
        MatrixStack matrices = context.matrixStack();
        Vec3d cam = cameraPos(client.gameRenderer.getCamera());
        double cx = wp.x + 0.5;
        double cz = wp.z + 0.5;
        double markerY = wp.y + 2.0;

        matrices.push();
        matrices.translate(cx - cam.x, markerY - cam.y, cz - cam.z);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());

        Matrix4f model = new Matrix4f(matrices.peek().getPositionMatrix());
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f mv = RenderSystem.getModelViewMatrix();
        Matrix4f combined = new Matrix4f(proj).mul(mv).mul(model);
        Vector4f clip = new Vector4f(0f, 0f, 0f, 1f);
        combined.transform(clip);
        float w = clip.w;

        boolean mvIsId = Math.abs(mv.m00() - 1f) < 1e-4f && Math.abs(mv.m11() - 1f) < 1e-4f
                && Math.abs(mv.m22() - 1f) < 1e-4f && Math.abs(mv.m33() - 1f) < 1e-4f
                && Math.abs(mv.m03()) < 1e-4f && Math.abs(mv.m13()) < 1e-4f && Math.abs(mv.m23()) < 1e-4f;

        LOGGER.info(
                "[waypointmenu] diag wp={} rel=({}) modelT=({}) proj=({}) mvId={} ndc=({}) w={}",
                wp.name,
                String.format("%.1f,%.1f,%.1f", cx - cam.x, markerY - cam.y, cz - cam.z),
                String.format("%.1f,%.1f,%.1f", model.m30(), model.m31(), model.m32()),
                String.format("%.2f,%.2f", proj.m00(), proj.m11()),
                mvIsId,
                String.format("%.3f,%.3f,%.3f", clip.x / w, clip.y / w, clip.z / w),
                String.format("%.3f", w));
        matrices.pop();
    }
    //?}

    /**
     * A flat, camera-facing diamond (rhombus): a single quad billboarded so it
     * appears the same from every viewing angle.
     */
    //? if !=1.21.9 {
    //? if >=1.21.10 {
    private static void drawFlatDiamond(MatrixStack.Entry m, VertexConsumer vc,
                                        float w, float h, float r, float g, float b, float a) {
        vc.vertex(m, 0, h, 0).color(r, g, b, a);   // top
        vc.vertex(m, w, 0, 0).color(r, g, b, a);   // right
        vc.vertex(m, 0, -h, 0).color(r, g, b, a);  // bottom
        vc.vertex(m, -w, 0, 0).color(r, g, b, a);  // left
    }
    //?} else {
    private static void drawFlatDiamond(Matrix4f m, VertexConsumer vc,
                                        float w, float h, float r, float g, float b, float a) {
        // On 1.20.x each .vertex().color() only stages one vertex; .next() is what commits
        // it (increments vertexCount). Without it the buffer ends with 0 vertices and
        // drawWithGlobalProgram renders nothing. 1.21 removed .next() from VertexConsumer —
        // vertex() auto-advances there instead.
        //? if <1.21 {
        vc.vertex(m, 0, h, 0).color(r, g, b, a).next();   // top
        vc.vertex(m, w, 0, 0).color(r, g, b, a).next();   // right
        vc.vertex(m, 0, -h, 0).color(r, g, b, a).next();  // bottom
        vc.vertex(m, -w, 0, 0).color(r, g, b, a).next();  // left
        //?} else {
        vc.vertex(m, 0, h, 0).color(r, g, b, a);   // top
        vc.vertex(m, w, 0, 0).color(r, g, b, a);   // right
        vc.vertex(m, 0, -h, 0).color(r, g, b, a);  // bottom
        vc.vertex(m, -w, 0, 0).color(r, g, b, a);  // left
        //?}
    }
    //?}
    //?}

    /**
     * Version-agnostic camera position: {@code getPos()} is renamed to
     * {@code getCameraPos()} at 1.21.6, and the old getter is dropped at 1.21.11.
     */
    //? if >=1.21.6 {
    private static Vec3d cameraPos(Camera camera) {
        return camera.getCameraPos();
    }
    //?} else {
    private static Vec3d cameraPos(Camera camera) {
        return camera.getPos();
    }
    //?}
}
