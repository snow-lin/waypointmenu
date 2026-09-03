package com.waypointmenu.screen;

import com.waypointmenu.ClientCompat;
import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for a single waypoint: name, dimension, highlight color, integer
 * coordinates, the command set, and a multi-line description box.
 *
 * <p>The panel height is adaptive: the description box shrinks from three lines
 * down to one when the screen is too short, so nothing ever overflows the top
 * or bottom edge.</p>
 */
public class WaypointEditScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int VISIBLE_COMMANDS = 3;

    /** Horizontal padding inside the panel and gap from the screen edge. */
    private static final int PAD = 12;
    private static final int MARGIN = 6;

    private static final int FIELD_H = 18;
    private static final int BTN_H = 18;
    private static final int ROW_PITCH = 20;

    /** Vertical layout offsets measured from the panel's top edge. */
    private static final int TITLE_Y = 4;
    private static final int DIVIDER_Y = 15;
    private static final int NAME_LABEL_Y = 17;
    private static final int NAME_FIELD_Y = 26;
    private static final int DIM_LABEL_Y = 46;
    private static final int DIM_FIELD_Y = 55;
    private static final int COORDS_LABEL_Y = 75;
    private static final int COORDS_FIELD_Y = 84;
    private static final int CMD_LABEL_Y = 104;
    private static final int CMD_ROW0_Y = 113;
    private static final int DESC_LABEL_Y = 173;
    private static final int DESC_BOX_Y = 182;

    /** Description box: 9px/line + 8px widget padding, so 35px == 3 lines. */
    private static final int DESC_MIN_H = 17; // one line
    private static final int DESC_MAX_H = 35; // three lines
    /** Everything in the panel except the description box height. */
    private static final int FIXED_H = DESC_BOX_Y + BTN_H + MARGIN + 2;

    private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    private static final String[] DIMENSION_KEYS = {
            "waypointmenu.dimension.overworld",
            "waypointmenu.dimension.nether",
            "waypointmenu.dimension.end"
    };

    /** Preset highlight colors offered as clickable swatches (ARGB). */
    private static final int[] COLORS = {
            0xFF00E6C0, 0xFFFFFFFF, 0xFFFF5555, 0xFFFFAA00,
            0xFFFFFF55, 0xFF55FF55, 0xFF55AAFF, 0xFFFF55FF
    };
    private static final int SWATCH_SIZE = 16;
    private static final int SWATCH_GAP = 5;

    private final WaypointManager manager = WaypointManager.getInstance();
    private final Waypoint waypoint;
    private final Screen parent;
    /** Whether this waypoint is not yet in the manager and must be added on save. */
    private final boolean isNew;
    private final List<String> commands;

    private int descH = DESC_MAX_H;
    private int commandScroll = 0;
    private int dimensionIndex = 0;
    private int selectedColor = Waypoint.DEFAULT_COLOR;
    private boolean updating = false;

    private EditBox nameField;
    private MultiLineEditBox descriptionField;
    private Button dimButton;
    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private final EditBox[] commandFields = new EditBox[VISIBLE_COMMANDS];
    private final Button[] removeButtons = new Button[VISIBLE_COMMANDS];
    private final Button[] insertButtons = new Button[VISIBLE_COMMANDS];

    public WaypointEditScreen(Waypoint waypoint, Screen parent, boolean isNew) {
        super(Component.translatable("screen.waypointmenu.edit.title"));
        this.waypoint = waypoint;
        this.parent = parent;
        this.isNew = isNew;
        this.commands = new ArrayList<>(waypoint.commands == null ? new ArrayList<>() : waypoint.commands);
        this.selectedColor = waypoint.color == 0 ? Waypoint.DEFAULT_COLOR : waypoint.color;
    }

    @Override
    protected void init() {
        // Fit the description box to whatever vertical space the screen offers.
        int available = this.height - 2 * MARGIN;
        descH = Math.min(DESC_MAX_H, Math.max(DESC_MIN_H, available - FIXED_H));

        int px = panelX();
        int py = panelY();

        nameField = new EditBox(this.font, px + PAD, py + NAME_FIELD_Y, PANEL_W - 2 * PAD, FIELD_H, Component.empty());
        nameField.setMaxLength(64);
        nameField.setValue(waypoint.name);
        addRenderableWidget(nameField);

        dimensionIndex = dimensionIndexOf(waypoint.dimension);
        dimButton = Button.builder(Component.translatable(DIMENSION_KEYS[dimensionIndex]), b -> cycleDimension())
                .bounds(px + PAD, py + DIM_FIELD_Y, 140, FIELD_H)
                .build();
        addRenderableWidget(dimButton);

        xField = new EditBox(this.font, px + PAD, py + COORDS_FIELD_Y, 108, FIELD_H, Component.empty());
        yField = new EditBox(this.font, px + 128, py + COORDS_FIELD_Y, 108, FIELD_H, Component.empty());
        zField = new EditBox(this.font, px + 244, py + COORDS_FIELD_Y, 108, FIELD_H, Component.empty());
        for (EditBox f : new EditBox[]{xField, yField, zField}) {
            f.setMaxLength(10);
            addRenderableWidget(f);
        }
        xField.setValue(String.valueOf(waypoint.x));
        yField.setValue(String.valueOf(waypoint.y));
        zField.setValue(String.valueOf(waypoint.z));

        // Command rows: text field, remove (✕), and insert-below (＋) buttons.
        for (int i = 0; i < VISIBLE_COMMANDS; i++) {
            final int slot = i;
            int rowY = py + CMD_ROW0_Y + i * ROW_PITCH;

            EditBox tf = new EditBox(this.font, px + PAD, rowY, PANEL_W - 64, FIELD_H, Component.empty());
            tf.setMaxLength(256);
            tf.setHint(Component.translatable("waypointmenu.placeholder.command"));
            tf.setResponder(text -> {
                if (!updating) {
                    setCommand(slot, text);
                }
            });
            commandFields[i] = tf;
            addRenderableWidget(tf);

            Button remove = Button.builder(Component.literal("✕"), b -> removeCommand(slot))
                    .bounds(px + PANEL_W - 48, rowY, 16, FIELD_H).build();
            removeButtons[i] = remove;
            addRenderableWidget(remove);

            Button insert = Button.builder(Component.literal("＋"), b -> insertCommandBelow(slot))
                    .bounds(px + PANEL_W - 28, rowY, 16, FIELD_H).build();
            insertButtons[i] = insert;
            addRenderableWidget(insert);
        }

        // Multi-line description box: auto-wraps and scrolls past its visible
        // lines, styled via the fluent builder. Note: build()'s last argument is
        // the narration message, not the text — the initial text must be set
        // explicitly via setValue().
        descriptionField = MultiLineEditBox.builder()
                .setX(px + PAD)
                .setY(py + DESC_BOX_Y)
                .setPlaceholder(Component.translatable("waypointmenu.placeholder.description"))
                .setTextColor(0xFFE0E0E0)
                .setTextShadow(true)
                .setCursorColor(0xFFFFFFFF)
                .setShowBackground(true)
                .setShowDecorations(true)
                .build(this.font, PANEL_W - 2 * PAD, descH, Component.translatable("waypointmenu.field.description"));
        descriptionField.setValue(waypoint.description == null ? "" : waypoint.description);
        addRenderableWidget(descriptionField);

        // Bottom bar.
        int saveY = py + DESC_BOX_Y + descH + 2;
        addRenderableWidget(Button.builder(Component.translatable("waypointmenu.button.save"), b -> save())
                .bounds(px + PAD, saveY, 160, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("waypointmenu.button.cancel"), b -> this.onClose())
                .bounds(px + PANEL_W - 172, saveY, 160, BTN_H).build());

        refreshCommandFields();
        setInitialFocus(nameField);
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelH() {
        return FIXED_H + descH;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int colorStartX() {
        return panelX() + 164;
    }

    private int colorY() {
        return panelY() + DIM_FIELD_Y;
    }

    private int swatchAt(double mouseX, double mouseY) {
        int x0 = colorStartX();
        int y0 = colorY();
        for (int i = 0; i < COLORS.length; i++) {
            int sx = x0 + i * (SWATCH_SIZE + SWATCH_GAP);
            if (mouseX >= sx && mouseX < sx + SWATCH_SIZE && mouseY >= y0 && mouseY < y0 + SWATCH_SIZE) {
                return i;
            }
        }
        return -1;
    }

    private void cycleDimension() {
        dimensionIndex = (dimensionIndex + 1) % DIMENSIONS.length;
        if (dimButton != null) {
            dimButton.setMessage(Component.translatable(DIMENSION_KEYS[dimensionIndex]));
        }
    }

    private int dimensionIndexOf(String dimension) {
        for (int i = 0; i < DIMENSIONS.length; i++) {
            if (DIMENSIONS[i].equals(dimension)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, 0x80000000);
        int px = panelX();
        int py = panelY();
        Ui.drawFrostedPanel(gui, px, py, PANEL_W, panelH());

        // Header title inside the panel, with a subtle divider below it.
        Ui.drawCenteredText(gui, this.font, this.title, this.width / 2, py + TITLE_Y, 0xFFFFFFFF);
        gui.fill(px + PAD, py + DIVIDER_Y, px + PANEL_W - PAD, py + DIVIDER_Y + 1, 0x1EFFFFFF);

        gui.text(this.font, Component.translatable("waypointmenu.field.name"), px + PAD, py + NAME_LABEL_Y, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.field.dimension"), px + PAD, py + DIM_LABEL_Y, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.field.color"), colorStartX(), py + DIM_LABEL_Y, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.field.coords"), px + PAD, py + COORDS_LABEL_Y, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.field.commands"), px + PAD, py + CMD_LABEL_Y, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.field.description"), px + PAD, py + DESC_LABEL_Y, 0xFFAAAAAA, false);

        // Color swatches.
        int sy = colorY();
        for (int i = 0; i < COLORS.length; i++) {
            int sx = colorStartX() + i * (SWATCH_SIZE + SWATCH_GAP);
            gui.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, COLORS[i]);
            if (COLORS[i] == selectedColor) {
                Ui.drawBorder(gui, sx - 1, sy - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xFFFFFFFF);
            } else {
                Ui.drawBorder(gui, sx, sy, SWATCH_SIZE, SWATCH_SIZE, 0x40FFFFFF);
            }
        }

        drawCommandScrollbar(gui, px, py);

        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    /** Vertical scrollbar for the command list, shown only when there are >3 commands. */
    private void drawCommandScrollbar(GuiGraphicsExtractor gui, int px, int py) {
        int total = commands.size();
        if (total <= VISIBLE_COMMANDS) {
            return;
        }
        int trackX = px + PANEL_W - 8;
        int trackTop = py + CMD_ROW0_Y;
        int trackH = VISIBLE_COMMANDS * ROW_PITCH - 2;
        int thumbH = Math.max(8, trackH * VISIBLE_COMMANDS / total);
        int maxScroll = total - VISIBLE_COMMANDS;
        int thumbY = trackTop + (trackH - thumbH) * commandScroll / maxScroll;
        gui.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0x26FFFFFF);
        gui.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80FFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0) {
            int idx = swatchAt(event.x(), event.y());
            if (idx >= 0) {
                selectedColor = COLORS[idx];
                return true;
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Description box handles its own scrollbar; elsewhere the wheel scrolls
        // the command list (replaces the old ▲/▼ buttons).
        if (descriptionField.isMouseOver(mouseX, mouseY)) {
            return descriptionField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int px = panelX();
        int py = panelY();
        int cmdTop = py + CMD_ROW0_Y;
        int cmdBottom = py + CMD_ROW0_Y + (VISIBLE_COMMANDS - 1) * ROW_PITCH + FIELD_H;
        if (mouseX >= px && mouseX < px + PANEL_W && mouseY >= cmdTop && mouseY < cmdBottom) {
            commandScroll -= (int) verticalAmount;
            clampScroll();
            refreshCommandFields();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            ClientCompat.setScreen(this.minecraft, parent);
        } else {
            super.onClose();
        }
    }

    private void refreshCommandFields() {
        updating = true;
        for (int i = 0; i < VISIBLE_COMMANDS; i++) {
            int index = commandScroll + i;
            boolean has = index >= 0 && index < commands.size();
            commandFields[i].setValue(has ? commands.get(index) : "");
            commandFields[i].setEditable(true);
            removeButtons[i].active = has;
            insertButtons[i].active = true;
        }
        updating = false;
    }

    private void setCommand(int slot, String text) {
        int index = commandScroll + slot;
        while (commands.size() <= index) {
            commands.add("");
        }
        commands.set(index, text);
    }

    private void removeCommand(int slot) {
        int index = commandScroll + slot;
        if (index >= 0 && index < commands.size()) {
            commands.remove(index);
            clampScroll();
            refreshCommandFields();
        }
    }

    private void insertCommandBelow(int slot) {
        int index = Math.min(commandScroll + slot + 1, commands.size());
        commands.add(index, "");
        if (index >= commandScroll + VISIBLE_COMMANDS) {
            commandScroll = index - VISIBLE_COMMANDS + 1;
        }
        clampScroll();
        refreshCommandFields();
    }

    private void clampScroll() {
        commandScroll = Math.max(0, Math.min(commandScroll, Math.max(0, commands.size() - VISIBLE_COMMANDS)));
    }

    private void save() {
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(xField.getValue().trim());
            y = Integer.parseInt(yField.getValue().trim());
            z = Integer.parseInt(zField.getValue().trim());
        } catch (NumberFormatException e) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.translatable("waypointmenu.message.invalid_number", "X / Y / Z"));
            }
            return;
        }

        waypoint.name = nameField.getValue().trim();
        if (waypoint.name.isEmpty()) {
            waypoint.name = "Waypoint";
        }
        waypoint.description = descriptionField.getValue().trim();
        waypoint.dimension = DIMENSIONS[dimensionIndex];
        waypoint.color = selectedColor;
        waypoint.x = x;
        waypoint.y = y;
        waypoint.z = z;

        // Drop every blank command line.
        List<String> cleaned = new ArrayList<>();
        for (String c : commands) {
            if (!c.trim().isEmpty()) {
                cleaned.add(c);
            }
        }
        waypoint.commands = cleaned;

        // A brand-new waypoint is only added to the manager here, so cancelling
        // the editor never leaves an uncommitted point in the list.
        if (isNew) {
            manager.addWaypoint(waypoint);
        } else {
            manager.markDirty();
        }
        if (this.minecraft != null && this.minecraft.player != null) {
            if (isNew) {
                this.minecraft.player.sendSystemMessage(Component.translatable("waypointmenu.message.recorded", waypoint.name));
            } else {
                this.minecraft.player.sendSystemMessage(Component.translatable("waypointmenu.message.saved"));
            }
        }
        this.onClose();
    }
}
