package com.waypointmenu.screen;

import com.waypointmenu.ClientCompat;
import com.waypointmenu.command.CommandSetExecutor;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix3x2fStack;

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
        super(Component.translatable("screen.waypointmenu.list.title"));
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
        addRenderableWidget(Button.builder(
                        Component.translatable("waypointmenu.button.add"),
                        b -> addCurrentPosition())
                .bounds(listLeft() + 8, panelY + PANEL_H - 22, btnW, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("waypointmenu.button.close"),
                        b -> this.onClose())
                .bounds(listLeft() + 8 + btnW + 16, panelY + PANEL_H - 22, btnW, 20)
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
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        // Dim the world behind the overlay.
        gui.fill(0, 0, this.width, this.height, 0x80000000);

        // Frosted-glass panel background.
        Ui.drawFrostedPanel(gui, panelX, panelY, PANEL_W, PANEL_H);

        // Heading centered above the panel (sidebar + list area).
        float titleScale = 1.5f;
        // The heading's draw anchor (y=5) renders at 5*titleScale px from the top.
        // Place it a quarter of the way from that top anchor down to the panel.
        double titleTop = 5.0 * titleScale;
        int titleY = (int) Math.round((titleTop + (panelY - titleTop) / 4.0) / titleScale);
        Matrix3x2fStack matrices = gui.pose();
        matrices.pushMatrix();
        matrices.scale(titleScale, titleScale);
        int titleCenterX = (int) (this.width / (2 * titleScale));
        Ui.drawCenteredText(gui, this.font, this.title, titleCenterX, titleY, 0xFFFFFFFF);
        matrices.popMatrix();

        // Sidebar: dimension filter with a divider separating it from the list.
        gui.fill(listLeft(), listTop, listLeft() + 1, listBottom, 0x1EFFFFFF);
        for (int i = 0; i < FILTER_KEYS.length; i++) {
            int bx = sidebarBtnX();
            int by = sidebarBtnY(i);
            boolean active = i == filterIndex;
            boolean hovered = mouseX >= bx && mouseX < bx + SB_BTN_W && mouseY >= by && mouseY < by + SB_BTN_H;
            int bg = active ? 0xFF2E3A48 : (hovered ? 0x28FFFFFF : 0x12FFFFFF);
            Ui.drawRoundedRect(gui, bx, by, bx + SB_BTN_W, by + SB_BTN_H, 4, bg);
            Ui.drawCenteredText(gui, this.font, Component.translatable(FILTER_KEYS[i]),
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
            Ui.drawCenteredText(gui, this.font,
                    Component.translatable("waypointmenu.message.empty"),
                    listCenterX,
                    listTop + (viewport / 2) - 5,
                    0xFF808080);
        } else {
            gui.enableScissor(listLeft(), listTop, panelX + PANEL_W, listBottom);
            for (int i = 0; i < waypoints.size(); i++) {
                int y = listTop + i * ROW_HEIGHT - scrollOffset;
                if (y + ROW_HEIGHT <= listTop || y >= listBottom) {
                    continue;
                }
                renderRow(gui, mouseX, mouseY, waypoints.get(i), y);
            }
            gui.disableScissor();
        }

        // Drop-position indicator while dragging.
        if (dragActive && dragWp != null) {
            int indicatorY = listTop + dragTargetSlot * ROW_HEIGHT - scrollOffset;
            gui.fill(listLeft() + 4, indicatorY - 1, panelX + PANEL_W - 4, indicatorY + 1, 0xFFFFFFFF);

            // Translucent copy of the dragged row that follows the mouse.
            drawFloatingRow(gui, dragWp, (int) dragMouseY - ROW_HEIGHT / 2);
        }

        // Scrollbar.
        if (maxScroll > 0) {
            int trackH = viewport;
            int thumbH = Math.max(24, trackH * viewport / contentHeight);
            int thumbY = listTop + (int) ((long) (trackH - thumbH) * scrollOffset / maxScroll);
            gui.fill(panelX + PANEL_W - 4, listTop, panelX + PANEL_W - 2, listBottom, 0x33FFFFFF);
            gui.fill(panelX + PANEL_W - 4, thumbY, panelX + PANEL_W - 2, thumbY + thumbH, 0xFF808080);
        }

        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphicsExtractor gui, int mouseX, int mouseY, Waypoint wp, int y) {
        boolean highlighted = manager.isHighlighted(wp);
        int left = listLeft();
        boolean hovered = mouseX >= left && mouseX < panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_HEIGHT;

        // The dragged row renders as a translucent copy at the mouse (see
        // extractRenderState); leave a faint placeholder in its original slot.
        if (dragActive && dragWp == wp) {
            Ui.drawRow(gui, left + 4, y, PANEL_W - SIDEBAR_W - 8, ROW_HEIGHT - 2, false);
            gui.fill(left + 4, y, left + 4 + PANEL_W - SIDEBAR_W - 8, y + ROW_HEIGHT - 2, 0x14000000);
            return;
        }

        Ui.drawRow(gui, left + 4, y, PANEL_W - SIDEBAR_W - 8, ROW_HEIGHT - 2, hovered);

        // Color indicator dot: bright when highlighted, dimmed otherwise.
        int dotColor = highlighted ? wp.color : ((wp.color & 0x00FFFFFF) | 0x66000000);
        Ui.drawRoundedRect(gui, left + 10, y + 10, left + 18, y + 18, 3, dotColor);

        // Name (line 1).
        int nameColor = highlighted ? wp.color : 0xFFFFFFFF;
        gui.text(this.font, Component.literal(wp.name), left + 24, y + 3, nameColor, true);

        // Action buttons on the right: highlight / run / edit / delete.
        int btnY = y + (ROW_HEIGHT - BTN) / 2;
        int delX = panelX + PANEL_W - 6 - BTN;
        int editX = delX - BTN - BTN_GAP;
        int runX = editX - BTN - BTN_GAP;
        int hlX = runX - BTN - BTN_GAP;

        // Line 2: coordinates + dimension, followed by a dimmed description snippet.
        MutableComponent coordsText = Component.literal(String.format("%d, %d, %d  ", wp.x, wp.y, wp.z)).append(dimensionName(wp.dimension));
        int coordsColor = highlighted ? wp.color : 0xFFAAAAAA;
        gui.text(this.font, coordsText, left + 24, y + 15, coordsColor, true);

        String desc = wp.description == null ? "" : wp.description.trim().replace('\n', ' ');
        if (!desc.isEmpty()) {
            int coordsW = this.font.width(coordsText);
            int avail = hlX - (left + 24) - 4;
            String sep = "  ·  ";
            int snippetMax = avail - coordsW - this.font.width(sep);
            if (snippetMax > 20) {
                gui.text(this.font, Component.literal(sep + truncateToWidth(desc, snippetMax)),
                        left + 24 + coordsW, y + 15, 0xFF888888, true);
            }
        }

        drawActionButton(gui, hlX, btnY, "◆", mouseX, mouseY, highlighted ? wp.color : 0xFF808080);
        drawActionButton(gui, runX, btnY, "▶", mouseX, mouseY, 0xFF40C040);
        drawActionButton(gui, editX, btnY, "✎", mouseX, mouseY, 0xFF4080E0);
        drawActionButton(gui, delX, btnY, "✕", mouseX, mouseY, 0xFFE04040);

        rows.add(new Row(wp, y, hlX, runX, editX, delX, btnY));

        // Tooltips for the action buttons.
        if (mouseX >= hlX && mouseX < hlX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            gui.setTooltipForNextFrame(Component.translatable("waypointmenu.tooltip.highlight"), mouseX, mouseY);
        } else if (mouseX >= runX && mouseX < runX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            gui.setTooltipForNextFrame(Component.translatable("waypointmenu.tooltip.run"), mouseX, mouseY);
        } else if (mouseX >= editX && mouseX < editX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            gui.setTooltipForNextFrame(Component.translatable("waypointmenu.tooltip.edit"), mouseX, mouseY);
        } else if (mouseX >= delX && mouseX < delX + BTN && mouseY >= btnY && mouseY < btnY + BTN) {
            gui.setTooltipForNextFrame(Component.translatable("waypointmenu.tooltip.delete"), mouseX, mouseY);
        } else if (mouseX >= left && mouseX < panelX + PANEL_W && mouseY >= y && mouseY < y + ROW_HEIGHT) {
            // With right-click teleport disabled, the row only toggles highlight;
            // otherwise it advertises both the left-click toggle and right-click teleport.
            Component rowTooltip = Component.translatable(WaypointConfig.get().rightClickTeleport
                    ? "waypointmenu.tooltip.row"
                    : "waypointmenu.tooltip.highlight");
            gui.setTooltipForNextFrame(rowTooltip, mouseX, mouseY);
        }
    }

    /** Draws the dragged row as a translucent copy that follows the mouse. */
    private void drawFloatingRow(GuiGraphicsExtractor gui, Waypoint wp, int y) {
        int x = listLeft() + 4;
        int w = PANEL_W - SIDEBAR_W - 8;

        Ui.drawRoundedRect(gui, x, y, x + w, y + ROW_HEIGHT - 2, 6, 0x662E3A48);
        Ui.drawRoundedRect(gui, x + 6, y + 10, x + 14, y + 18, 3, wp.color);

        gui.text(this.font, Component.literal(wp.name), x + 20, y + 3, 0xB0FFFFFF, true);
        MutableComponent coordsText = Component.literal(String.format("%d, %d, %d  ", wp.x, wp.y, wp.z)).append(dimensionName(wp.dimension));
        gui.text(this.font, coordsText, x + 20, y + 15, 0xB0AAAAAA, true);
    }

    private void drawActionButton(GuiGraphicsExtractor gui, int x, int y, String glyph, int mouseX, int mouseY, int color) {
        boolean hovered = mouseX >= x && mouseX < x + BTN && mouseY >= y && mouseY < y + BTN;
        Ui.drawButton(gui, x, y, BTN, hovered);
        Ui.drawCenteredText(gui, this.font, Component.literal(glyph), x + BTN / 2, y + 3, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0 && handleClick(event.x(), event.y())) {
            return true;
        }
        if (event.button() == 1 && handleRightClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, bl);
    }

    /** Shared click handling for the mouse-click signature. */
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
        Minecraft client = this.minecraft;
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        // Creative players may always teleport, ignoring both toggles.
        boolean creative = client.player.isCreative();
        if (!creative && !WaypointConfig.get().rightClickTeleport) {
            return;
        }
        ClientPacketListener handler = client.getConnection();
        if (handler == null) {
            return;
        }
        String currentDim = client.level.dimension().identifier().toString();
        String coords = String.format("%.1f %d %.1f", wp.x + 0.5, wp.y, wp.z + 0.5);
        // Same-dimension teleport uses a plain /tp; cross-dimension wraps it in
        // /execute in <dimension> so the destination resolves in that world.
        if (currentDim.equals(wp.dimension)) {
            handler.sendCommand("tp " + coords);
        } else if (creative || WaypointConfig.get().crossDimensionTeleport) {
            handler.sendCommand("execute in " + wp.dimension + " run tp @s " + coords);
        } else {
            client.player.sendOverlayMessage(Component.translatable("waypointmenu.message.cross_dimension_off"));
            return;
        }
        client.player.sendOverlayMessage(Component.translatable("waypointmenu.message.teleported", wp.name));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (handleDrag(event.x(), event.y())) {
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    /** Shared drag handling for the mouse-dragged signature. */
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

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (handleRelease()) {
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Shared release handling for the mouse-released signature. */
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (verticalAmount * ROW_HEIGHT), maxScroll));
        return true;
    }

    private void addCurrentPosition() {
        Minecraft client = this.minecraft;
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        BlockPos pos = client.player.blockPosition();
        String name = "Waypoint " + (manager.getWaypoints().size() + 1);
        // Build the waypoint but don't add it yet: it is only committed to the
        // manager when the editor's save button is pressed, so cancelling the
        // editor leaves the list unchanged.
        Waypoint wp = new Waypoint("", name, dimension, pos.getX(), pos.getY(), pos.getZ(), new ArrayList<>());
        wp.color = RANDOM_COLORS[RANDOM.nextInt(RANDOM_COLORS.length)];
        openNewEditor(wp);
    }

    private void openEditor(Waypoint wp) {
        if (this.minecraft != null) {
            ClientCompat.setScreen(this.minecraft, new WaypointEditScreen(wp, this, false));
        }
    }

    private void openNewEditor(Waypoint wp) {
        if (this.minecraft != null) {
            ClientCompat.setScreen(this.minecraft, new WaypointEditScreen(wp, this, true));
        }
    }

    private void toggleHighlight(Waypoint wp) {
        boolean on = manager.toggleHighlight(wp);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendOverlayMessage(
                    Component.translatable(on ? "waypointmenu.message.highlight_on" : "waypointmenu.message.highlight_off", wp.name));
        }
    }

    private void runCommands(Waypoint wp) {
        if (wp.commands == null || wp.commands.isEmpty()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.translatable("waypointmenu.message.no_commands"));
            }
            return;
        }
        CommandSetExecutor.execute(wp.commands);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendOverlayMessage(Component.translatable("waypointmenu.message.commands_run", wp.commands.size()));
        }
    }

    private void delete(Waypoint wp) {
        manager.removeWaypoint(wp);
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.translatable("waypointmenu.message.deleted"));
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
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        int ellipsisW = this.font.width("…");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (this.font.width(sb.toString() + c) + ellipsisW > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb + "…";
    }

    /** Localizes a raw dimension id to its display name (主世界 / 下界 / 末地). */
    private static Component dimensionName(String dimension) {
        String key;
        if ("minecraft:the_nether".equals(dimension)) {
            key = "waypointmenu.dimension.nether";
        } else if ("minecraft:the_end".equals(dimension)) {
            key = "waypointmenu.dimension.end";
        } else {
            key = "waypointmenu.dimension.overworld";
        }
        return Component.translatable(key);
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
