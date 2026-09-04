package com.waypointmenu.screen;

import com.waypointmenu.command.CommandSetExecutor;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
//? if >=1.21.9 {
import net.minecraft.client.gui.Click;
//?}
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
//? if >=1.21.6 {
import org.joml.Matrix3x2fStack;
//?} else {
import net.minecraft.client.util.math.MatrixStack;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main screen: a left sidebar filters by dimension, and a scrollable list of
 * recorded locations fills the rest. Each row shows the name, coordinates,
 * localized dimension and a short description snippet; the right-hand buttons
 * run the command set, edit the waypoint, or delete it.
 */
public class WaypointListScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 200;
    private static final int SIDEBAR_W = 64;
    private static final int ROW_HEIGHT = 28;
    private static final int BTN = 16;
    private static final int BTN_GAP = 4;

    /** Sidebar filter buttons. */
    private static final int SB_BTN_W = 52;
    private static final int SB_BTN_H = 20;
    private static final int SB_BTN_PITCH = 26;

    /** Filter dimensions; a {@code null} entry means "show all". */
    private static final String[] FILTER_DIMS = {null, "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final String[] FILTER_KEYS = {
            "waypointmenu.filter.all",
            "waypointmenu.dimension.overworld",
            "waypointmenu.dimension.nether",
            "waypointmenu.dimension.end"
    };

    /** Colors randomly assigned to newly created waypoints (ARGB). */
    private static final int[] RANDOM_COLORS = {
            0xFF00E6C0, 0xFF55AAFF, 0xFFFFAA00, 0xFFFF5555,
            0xFF55FF55, 0xFFFF55FF, 0xFFFFFF55, 0xFFAA66FF
    };
    private static final Random RANDOM = new Random();

    private final WaypointManager manager = WaypointManager.getInstance();

    private int panelX;
    private int panelY;
    private int listTop;
    private int listBottom;
    private int scrollOffset;
    private int maxScroll;
    private int filterIndex = 0;

    // Drag-to-reorder state.
    private Waypoint dragWp;
    private boolean dragActive;
    private double dragStartY;
    private double dragMouseY;
    private int dragTargetSlot = -1;

    private final List<Row> rows = new ArrayList<>();

    public WaypointListScreen() {
        super(Text.translatable("screen.waypointmenu.list.title"));
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = Math.max((this.height - PANEL_H) / 2, 26);
        listTop = panelY + 6;
        listBottom = panelY + PANEL_H - 26; // 6 * ROW_HEIGHT (168px) of list space
        dragWp = null;
        dragActive = false;
        dragTargetSlot = -1;

        // Bottom bar spans the list area (right of the sidebar), not the full panel.
        int btnW = 132;
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("waypointmenu.button.add"),
                        b -> addCurrentPosition())
                .dimensions(listLeft() + 8, panelY + PANEL_H - 22, btnW, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("waypointmenu.button.close"),
                        b -> this.close())
                .dimensions(listLeft() + 8 + btnW + 16, panelY + PANEL_H - 22, btnW, 20)
                .build());
    }

    private int listLeft() {
        return panelX + SIDEBAR_W;
    }

    private int sidebarBtnX() {
        return panelX + 6;
    }

    private int sidebarBtnY(int i) {
        return panelY + 8 + i * SB_BTN_PITCH;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw the background first so it stays beneath the overlay text:
        // through 1.21.5 the base Screen.render() runs renderBackground() at its
        // start, and this method calls super.render() at the end, so the
        // background would otherwise be painted on top of the text. From 1.21.6
        // the background is drawn outside render(), so no manual draw is needed.
        //? if >=1.20.2 {
        //? if <1.21.6 {
        //? if <1.20.6 {
        // 1.20.4 iterates its GUI layers in HashMap order, so even drawn first
        // the vanilla background texture can land on top; draw a fill-layer
        // gradient instead.
        context.fillGradient(0, 0, this.width, this.height, 0xFF1E1E2A, 0xFF0E0E16);
        //?} else {
        super.renderBackground(context, mouseX, mouseY, delta);
        //?}
        //?}
        //?}

        // Dim the world behind the overlay.
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Frosted-glass panel background.
        Ui.drawFrostedPanel(context, panelX, panelY, PANEL_W, PANEL_H);

        // Heading centered above the panel (sidebar + list area).
        float titleScale = 1.5f;
        // The heading's draw anchor (y=5) renders at 5*titleScale px from the top.
        // Place it a quarter of the way from that top anchor down to the panel.
        double titleTop = 5.0 * titleScale;
        int titleY = (int) Math.round((titleTop + (panelY - titleTop) / 4.0) / titleScale);
        //? if >=1.21.6 {
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(titleScale, titleScale);
        int titleCenterX = (int) (this.width / (2 * titleScale));
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, titleCenterX, titleY, 0xFFFFFFFF);
        matrices.popMatrix();
        //?} else {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(titleScale, titleScale, 1.0f);
        int titleCenterX = (int) (this.width / (2 * titleScale));
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, titleCenterX, titleY, 0xFFFFFFFF);
        matrices.pop();
        //?}

        // Sidebar: dimension filter with a divider separating it from the list.
        context.fill(listLeft(), listTop, listLeft() + 1, listBottom, 0x1EFFFFFF);
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            int bx = sidebarBtnX();
            int by = sidebarBtnY(i);
            boolean active = i == filterIndex;
            boolean hovered = mouseX >= bx && mouseX < bx + SB_BTN_W && mouseY >= by && mouseY < by + SB_BTN_H;
            int bg = active ? 0xFF2E3A48 : (hovered ? 0x28FFFFFF : 0x12FFFFFF);
            Ui.drawRoundedRect(context, bx, by, bx + SB_BTN_W, by + SB_BTN_H, 4, bg);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(FILTER_KEYS[i]),
                    bx + SB_BTN_W / 2, by + 5, active ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        List<Waypoint> waypoints = filterWaypoints();
        int contentHeight = waypoints.size() * ROW_HEIGHT;
        int viewport = listBottom - listTop;
        maxScroll = Math.max(0, contentHeight - viewport);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        rows.clear();

        if (waypoints.isEmpty()) {
            int listCenterX = listLeft() + (PANEL_W - SIDEBAR_W) / 2;
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("waypointmenu.message.empty"),
                    listCenterX,
                    listTop + (viewport / 2) - 5,
                    0xFF808080);
        } else {
            context.enableScissor(listLeft(), listTop, panelX + PANEL_W, listBottom);
            for (int i = 0; i < waypoints.size(); i++) {
                int y = listTop + i * ROW_HEIGHT - scrollOffset;
                if (y + ROW_HEIGHT <= listTop || y >= listBottom) {
                    continue;
                }
                renderRow(context, mouseX, mouseY, waypoints.get(i), y);
            }
            context.disableScissor();
        }

        // Drop-position indicator while dragging.
        if (dragActive && dragWp != null) {
            int indicatorY = listTop + dragTargetSlot * ROW_HEIGHT - scrollOffset;
            context.fill(listLeft() + 4, indicatorY - 1, panelX + PANEL_W - 4, indicatorY + 1, 0xFFFFFFFF);

            // Translucent copy of the dragged row that follows the mouse.
            drawFloatingRow(context, dragWp, (int) dragMouseY - ROW_HEIGHT / 2);
        }

        // Scrollbar.
        if (maxScroll > 0) {
            int trackH = viewport;
            int thumbH = Math.max(24, trackH * viewport / contentHeight);
            int thumbY = listTop + (int) ((long) (trackH - thumbH) * scrollOffset / maxScroll);
            context.fill(panelX + PANEL_W - 4, listTop, panelX + PANEL_W - 2, listBottom, 0x33FFFFFF);
            context.fill(panelX + PANEL_W - 4, thumbY, panelX + PANEL_W - 2, thumbY + thumbH, 0xFF808080);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    //? if >=1.20.2 {
    //? if <1.21.6 {
    /**
     * Neutralized: the background is drawn manually at the top of {@link #render}
     * so it stays beneath the overlay text. Drawing it again here (where the base
     * {@code Screen.render} runs it) would repaint it on top of the text.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }
    //?}
    //?}

    private void renderRow(DrawContext context, int mouseX, int mouseY, Waypoint wp, int y) {
        boolean highlighted = manager.isHighlighted(wp);
        int left = listLeft();
        boolean hovered = mouseX >= left && mouseX < panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // The dragged row renders as a translucent copy at the mouse (see
        // render()); leave a faint placeholder in its original slot.
        if (dragActive && dragWp == wp) {
            Ui.drawRow(context, left + 4, y, PANEL_W - SIDEBAR_W - 8, ROW_HEIGHT - 2, false);
            context.fill(left + 4, y, left + 4 + PANEL_W - SIDEBAR_W - 8, y + ROW_HEIGHT - 2, 0x14000000);
            return;
        }

        Ui.drawRow(context, left + 4, y, PANEL_W - SIDEBAR_W - 8, ROW_HEIGHT - 2, hovered);

        // Color indicator dot: bright when highlighted, dimmed otherwise.
        int dotColor = highlighted ? wp.color : ((wp.color & 0x00FFFFFF) | 0x66000000);
        Ui.drawRoundedRect(context, left + 10, y + 10, left + 18, y + 18, 3, dotColor);

        // Name (line 1).
        int nameColor = highlighted ? wp.color : 0xFFFFFFFF;
        context.drawText(this.textRenderer, Text.literal(wp.name), left + 24, y + 3, nameColor, true);

        // Action buttons on the right: highlight / run / edit / delete.
        int btnY = y + (ROW_HEIGHT - BTN) / 2;
        int delX = panelX + PANEL_W - 6 - BTN;
        int editX = delX - BTN - BTN_GAP;
        int runX = editX - BTN - BTN_GAP;
        int hlX = runX - BTN - BTN_GAP;

        // Line 2: coordinates + dimension, followed by a dimmed description snippet.
        MutableText coordsText = Text.literal(String.format("%d, %d, %d  ", wp.x, wp.y, wp.z)).append(dimensionName(wp.dimension));
        int coordsColor = highlighted ? wp.color : 0xFFAAAAAA;
        context.drawText(this.textRenderer, coordsText, left + 24, y + 15, coordsColor, true);

        String desc = wp.description == null ? "" : wp.description.trim().replace('\n', ' ');
        if (!desc.isEmpty()) {
            int coordsW = this.textRenderer.getWidth(coordsText);
            int avail = hlX - (left + 24) - 4;
            String sep = "  ·  ";
            int snippetMax = avail - coordsW - this.textRenderer.getWidth(sep);
            if (snippetMax > 20) {
                context.drawText(this.textRenderer, Text.literal(sep + truncateToWidth(desc, snippetMax)),
                        left + 24 + coordsW, y + 15, 0xFF888888, true);
            }
        }

        drawActionButton(context, hlX, btnY, "◆", mouseX, mouseY, highlighted ? wp.color : 0xFF808080);
        drawActionButton(context, runX, btnY, "▶", mouseX, mouseY, 0xFF40C040);
        drawActionButton(context, editX, btnY, "✎", mouseX, mouseY, 0xFF4080E0);
        drawActionButton(context, delX, btnY, "✕", mouseX, mouseY, 0xFFE04040);

        rows.add(new Row(wp, y, hlX, runX, editX, delX, btnY));

        // Tooltips for the action buttons.
        if (mouseX >= hlX && mouseX < hlX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            context.drawTooltip(this.textRenderer, Text.translatable("waypointmenu.tooltip.highlight"), mouseX, mouseY);
        } else if (mouseX >= runX && mouseX < runX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            context.drawTooltip(this.textRenderer, Text.translatable("waypointmenu.tooltip.run"), mouseX, mouseY);
        } else if (mouseX >= editX && mouseX < editX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            context.drawTooltip(this.textRenderer, Text.translatable("waypointmenu.tooltip.edit"), mouseX, mouseY);
        } else if (mouseX >= delX && mouseX < delX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            context.drawTooltip(this.textRenderer, Text.translatable("waypointmenu.tooltip.delete"), mouseX, mouseY);
        } else if (mouseX >= left && mouseX < panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_HEIGHT) {
            // With right-click teleport disabled, the row only toggles highlight;
            // otherwise it advertises both the left-click toggle and right-click teleport.
            Text rowTooltip = Text.translatable(WaypointConfig.get().rightClickTeleport
                    ? "waypointmenu.tooltip.row"
                    : "waypointmenu.tooltip.highlight");
            context.drawTooltip(this.textRenderer, rowTooltip, mouseX, mouseY);
        }
    }

    /** Draws the dragged row as a translucent copy that follows the mouse. */
    private void drawFloatingRow(DrawContext context, Waypoint wp, int y) {
        int x = listLeft() + 4;
        int w = PANEL_W - SIDEBAR_W - 8;

        Ui.drawRoundedRect(context, x, y, x + w, y + ROW_HEIGHT - 2, 6, 0x662E3A48);
        Ui.drawRoundedRect(context, x + 6, y + 10, x + 14, y + 18, 3, wp.color);

        context.drawText(this.textRenderer, Text.literal(wp.name), x + 20, y + 3, 0xB0FFFFFF, true);
        MutableText coordsText = Text.literal(String.format("%d, %d, %d  ", wp.x, wp.y, wp.z)).append(dimensionName(wp.dimension));
        context.drawText(this.textRenderer, coordsText, x + 20, y + 15, 0xB0AAAAAA, true);
    }

    private void drawActionButton(DrawContext context, int x, int y, String glyph, int mouseX, int mouseY, int color) {
        boolean hovered = mouseX >= x && mouseX < x + BTN && mouseY >= y && mouseY < y + BTN;
        Ui.drawButton(context, x, y, BTN, hovered);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(glyph), x + BTN / 2, y + 3, color);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() == 0 && handleClick(click.x(), click.y())) {
            return true;
        }
        if (click.button() == 1 && handleRightClick(click.x(), click.y())) {
            return true;
        }
        return super.mouseClicked(click, bl);
    }
    //?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleClick(mouseX, mouseY)) {
            return true;
        }
        if (button == 1 && handleRightClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    /** Shared click handling for the era-specific mouseClicked signatures. */
    private boolean handleClick(double mouseX, double mouseY) {
        // Sidebar filter buttons.
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            int bx = sidebarBtnX();
            int by = sidebarBtnY(i);
            if (mouseX >= bx && mouseX < bx + SB_BTN_W && mouseY >= by && mouseY < by + SB_BTN_H) {
                if (filterIndex != i) {
                    filterIndex = i;
                    scrollOffset = 0;
                }
                return true;
            }
        }

        for (Row row : rows) {
            if (mouseX >= row.hlX && mouseX < row.hlX + BTN && mouseY >= row.btnY && mouseY < row.btnY + BTN) {
                toggleHighlight(row.waypoint);
                return true;
            }
            if (mouseX >= row.delX && mouseX < row.delX + BTN && mouseY >= row.btnY && mouseY < row.btnY + BTN) {
                delete(row.waypoint);
                return true;
            }
            if (mouseX >= row.editX && mouseX < row.editX + BTN && mouseY >= row.btnY && mouseY < row.btnY + BTN) {
                openEditor(row.waypoint);
                return true;
            }
            if (mouseX >= row.runX && mouseX < row.runX + BTN && mouseY >= row.btnY && mouseY < row.btnY + BTN) {
                runCommands(row.waypoint);
                return true;
            }
            // Row body -> begin a potential drag (long-press to reorder).
            if (mouseX >= listLeft() && mouseX < panelX + PANEL_W && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
                dragWp = row.waypoint;
                dragActive = false;
                dragStartY = mouseY;
                dragMouseY = mouseY;
                return true;
            }
        }
        return false;
    }

    /** Right-click a row body (left of the action buttons) -> teleport to it. */
    private boolean handleRightClick(double mouseX, double mouseY) {
        for (Row row : rows) {
            if (mouseX >= listLeft() && mouseX < row.hlX
                    && mouseY >= row.y && mouseY < row.y + ROW_HEIGHT) {
                teleport(row.waypoint);
                return true;
            }
        }
        return false;
    }

    /** Teleports the player to a waypoint, across dimensions when needed. */
    private void teleport(Waypoint wp) {
        MinecraftClient client = this.client;
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        // Creative players may always teleport, ignoring both toggles.
        boolean creative = client.player.isCreative();
        if (!creative && !WaypointConfig.get().rightClickTeleport) {
            return;
        }
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return;
        }
        String currentDim = client.world.getRegistryKey().getValue().toString();
        String coords = String.format("%.1f %d %.1f", wp.x + 0.5, wp.y, wp.z + 0.5);
        // Same-dimension teleport uses a plain /tp; cross-dimension wraps it in
        // /execute in <dimension> so the destination resolves in that world.
        if (currentDim.equals(wp.dimension)) {
            handler.sendChatCommand("tp " + coords);
        } else if (creative || WaypointConfig.get().crossDimensionTeleport) {
            handler.sendChatCommand("execute in " + wp.dimension + " run tp @s " + coords);
        } else {
            client.player.sendMessage(Text.translatable("waypointmenu.message.cross_dimension_off"), true);
            return;
        }
        client.player.sendMessage(Text.translatable("waypointmenu.message.teleported", wp.name), true);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (handleDrag(click.x(), click.y())) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }
    //?} else {
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handleDrag(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    //?}

    /** Shared drag handling for the era-specific mouseDragged signatures. */
    private boolean handleDrag(double mouseX, double mouseY) {
        if (dragWp != null) {
            dragMouseY = mouseY;
            if (!dragActive && Math.abs(mouseY - dragStartY) > 4.0) {
                dragActive = true;
            }
            if (dragActive) {
                double relY = mouseY - listTop + scrollOffset;
                int slot = (int) Math.floor(relY / (double) ROW_HEIGHT);
                dragTargetSlot = Math.max(0, Math.min(slot, filterWaypoints().size()));
                return true;
            }
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseReleased(Click click) {
        if (handleRelease()) {
            return true;
        }
        return super.mouseReleased(click);
    }
    //?} else {
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handleRelease()) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    //?}

    /** Shared release handling for the era-specific mouseReleased signatures. */
    private boolean handleRelease() {
        if (dragWp != null) {
            Waypoint wp = dragWp;
            boolean wasActive = dragActive;
            int targetSlot = dragTargetSlot;
            dragWp = null;
            dragActive = false;
            dragTargetSlot = -1;
            if (wasActive) {
                List<Waypoint> filtered = filterWaypoints();
                int slot = Math.max(0, Math.min(targetSlot, filtered.size()));
                int toIndex = slot < filtered.size()
                        ? manager.getWaypoints().indexOf(filtered.get(slot))
                        : manager.getWaypoints().size();
                manager.moveWaypoint(wp, toIndex);
            } else {
                toggleHighlight(wp);
            }
            return true;
        }
        return false;
    }

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (verticalAmount * ROW_HEIGHT), maxScroll));
        return true;
    }
    //?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (amount * ROW_HEIGHT), maxScroll));
        return true;
    }
    //?}

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void addCurrentPosition() {
        MinecraftClient client = this.client;
        if (client == null || client.player == null || client.world == null) {
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        BlockPos pos = client.player.getBlockPos();
        String name = "Waypoint " + (manager.getWaypoints().size() + 1);
        // Build the waypoint but don't add it yet: it is only committed to the
        // manager when the editor's save button is pressed, so cancelling the
        // editor leaves the list unchanged.
        Waypoint wp = new Waypoint("", name, dimension, pos.getX(), pos.getY(), pos.getZ(), new ArrayList<>());
        wp.color = RANDOM_COLORS[RANDOM.nextInt(RANDOM_COLORS.length)];
        openNewEditor(wp);
    }

    private void openEditor(Waypoint wp) {
        if (this.client != null) {
            this.client.setScreen(new WaypointEditScreen(wp, this, false));
        }
    }

    private void openNewEditor(Waypoint wp) {
        if (this.client != null) {
            this.client.setScreen(new WaypointEditScreen(wp, this, true));
        }
    }

    private void toggleHighlight(Waypoint wp) {
        boolean on = manager.toggleHighlight(wp);
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(
                    Text.translatable(on ? "waypointmenu.message.highlight_on" : "waypointmenu.message.highlight_off", wp.name),
                    true);
        }
    }

    private void runCommands(Waypoint wp) {
        if (wp.commands == null || wp.commands.isEmpty()) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("waypointmenu.message.no_commands"), false);
            }
            return;
        }
        CommandSetExecutor.execute(wp.commands);
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.translatable("waypointmenu.message.commands_run", wp.commands.size()), true);
        }
    }

    private void delete(Waypoint wp) {
        manager.removeWaypoint(wp);
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.translatable("waypointmenu.message.deleted"), false);
        }
    }

    private List<Waypoint> filterWaypoints() {
        String dim = FILTER_DIMS[filterIndex];
        if (dim == null) {
            return manager.getWaypoints();
        }
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint wp : manager.getWaypoints()) {
            if (dim.equals(wp.dimension)) {
                out.add(wp);
            }
        }
        return out;
    }

    /** Truncates a string to fit {@code maxWidth} pixels, appending "…" when cut. */
    private String truncateToWidth(String s, int maxWidth) {
        if (this.textRenderer.getWidth(s) <= maxWidth) {
            return s;
        }
        int ellipsisW = this.textRenderer.getWidth("…");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (this.textRenderer.getWidth(sb.toString() + c) + ellipsisW > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + "…";
    }

    /** Localizes a raw dimension id to its display name (主世界 / 下界 / 末地). */
    private static Text dimensionName(String dimension) {
        String key;
        if ("minecraft:the_nether".equals(dimension)) {
            key = "waypointmenu.dimension.nether";
        } else if ("minecraft:the_end".equals(dimension)) {
            key = "waypointmenu.dimension.end";
        } else {
            key = "waypointmenu.dimension.overworld";
        }
        return Text.translatable(key);
    }

    private static class Row {
        final Waypoint waypoint;
        final int y;
        final int hlX;
        final int runX;
        final int editX;
        final int delX;
        final int btnY;

        Row(Waypoint waypoint, int y, int hlX, int runX, int editX, int delX, int btnY) {
            this.waypoint = waypoint;
            this.y = y;
            this.hlX = hlX;
            this.runX = runX;
            this.editX = editX;
            this.delX = delX;
            this.btnY = btnY;
        }
    }
}
