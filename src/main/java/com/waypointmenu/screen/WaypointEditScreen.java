package com.waypointmenu.screen;

import com.waypointmenu.data.Waypoint;
import com.waypointmenu.data.WaypointManager;
import com.waypointmenu.ui.Ui;
//? if >=1.21.9 {
import net.minecraft.client.gui.Click;
//?}
//? if >=1.21.5 {
import net.minecraft.client.gui.widget.EditBoxWidget;
//?}
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

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
    private final List<String> commands;

    private int descH = DESC_MAX_H;
    private int commandScroll = 0;
    private int dimensionIndex = 0;
    private int selectedColor = Waypoint.DEFAULT_COLOR;
    private boolean updating = false;

    private TextFieldWidget nameField;
    //? if >=1.21.5 {
    private EditBoxWidget descriptionField;
    //?} else {
    private TextFieldWidget descriptionField;
    //?}
    private ButtonWidget dimButton;
    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private final TextFieldWidget[] commandFields = new TextFieldWidget[VISIBLE_COMMANDS];
    private final ButtonWidget[] removeButtons = new ButtonWidget[VISIBLE_COMMANDS];
    private final ButtonWidget[] insertButtons = new ButtonWidget[VISIBLE_COMMANDS];

    public WaypointEditScreen(Waypoint waypoint, Screen parent) {
        super(Text.translatable("screen.waypointmenu.edit.title"));
        this.waypoint = waypoint;
        this.parent = parent;
        this.commands = new ArrayList<>(waypoint.commands == null ? new ArrayList<>() : waypoint.commands);
        this.selectedColor = waypoint.color == 0 ? Waypoint.DEFAULT_COLOR : waypoint.color;
    }

    @Override
    protected void init() {
        //? if >=1.21.5 {
        // Fit the description box to whatever vertical space the screen offers.
        int available = this.height - 2 * MARGIN;
        descH = Math.min(DESC_MAX_H, Math.max(DESC_MIN_H, available - FIXED_H));
        //?} else {
        // Pre-1.21.3 has no multi-line edit box; a single-line field is used.
        descH = FIELD_H;
        //?}

        int px = panelX();
        int py = panelY();

        nameField = new TextFieldWidget(this.textRenderer, px + PAD, py + NAME_FIELD_Y, PANEL_W - 2 * PAD, FIELD_H, Text.empty());
        nameField.setMaxLength(64);
        nameField.setText(waypoint.name);
        addDrawableChild(nameField);

        dimensionIndex = dimensionIndexOf(waypoint.dimension);
        dimButton = ButtonWidget.builder(Text.translatable(DIMENSION_KEYS[dimensionIndex]), b -> cycleDimension())
                .dimensions(px + PAD, py + DIM_FIELD_Y, 140, FIELD_H)
                .build();
        addDrawableChild(dimButton);

        xField = new TextFieldWidget(this.textRenderer, px + PAD, py + COORDS_FIELD_Y, 108, FIELD_H, Text.empty());
        yField = new TextFieldWidget(this.textRenderer, px + 128, py + COORDS_FIELD_Y, 108, FIELD_H, Text.empty());
        zField = new TextFieldWidget(this.textRenderer, px + 244, py + COORDS_FIELD_Y, 108, FIELD_H, Text.empty());
        for (TextFieldWidget f : new TextFieldWidget[]{xField, yField, zField}) {
            f.setTextPredicate(s -> s.matches("-?\\d*"));
            f.setMaxLength(10);
            addDrawableChild(f);
        }
        xField.setText(String.valueOf(waypoint.x));
        yField.setText(String.valueOf(waypoint.y));
        zField.setText(String.valueOf(waypoint.z));

        // Command rows: text field, remove (✕), and insert-below (＋) buttons.
        for (int i = 0; i < VISIBLE_COMMANDS; i++) {
            final int slot = i;
            int rowY = py + CMD_ROW0_Y + i * ROW_PITCH;

            TextFieldWidget tf = new TextFieldWidget(this.textRenderer, px + PAD, rowY, PANEL_W - 64, FIELD_H, Text.empty());
            tf.setMaxLength(256);
            tf.setPlaceholder(Text.translatable("waypointmenu.placeholder.command"));
            tf.setChangedListener(text -> {
                if (!updating) {
                    setCommand(slot, text);
                }
            });
            commandFields[i] = tf;
            addDrawableChild(tf);

            ButtonWidget remove = ButtonWidget.builder(Text.literal("✕"), b -> removeCommand(slot))
                    .dimensions(px + PANEL_W - 48, rowY, 16, FIELD_H).build();
            removeButtons[i] = remove;
            addDrawableChild(remove);

            ButtonWidget insert = ButtonWidget.builder(Text.literal("＋"), b -> insertCommandBelow(slot))
                    .dimensions(px + PANEL_W - 28, rowY, 16, FIELD_H).build();
            insertButtons[i] = insert;
            addDrawableChild(insert);
        }

        //? if >=1.21.6 {
        // Multi-line description box (1.21.6+): auto-wraps and scrolls past its
        // visible lines, styled via the fluent builder. Note: build()'s last
        // argument is the narration message, not the text — the initial text must
        // be set explicitly via setText().
        descriptionField = EditBoxWidget.builder()
                .x(px + PAD)
                .y(py + DESC_BOX_Y)
                .placeholder(Text.translatable("waypointmenu.placeholder.description"))
                .textColor(0xFFE0E0E0)
                .textShadow(true)
                .cursorColor(0xFFFFFFFF)
                .hasBackground(true)
                .hasOverlay(true)
                .build(this.textRenderer, PANEL_W - 2 * PAD, descH, Text.translatable("waypointmenu.field.description"));
        //?} elif >=1.21.5 {
        // 1.21.5 has the multi-line EditBoxWidget but not yet its fluent builder,
        // so build it from the plain constructor (placeholder, narration message).
        descriptionField = new EditBoxWidget(this.textRenderer, px + PAD, py + DESC_BOX_Y,
                PANEL_W - 2 * PAD, descH,
                Text.translatable("waypointmenu.placeholder.description"),
                Text.translatable("waypointmenu.field.description"));
        //?} else {
        // Pre-1.21.5: single-line description field (multi-line box is unavailable).
        descriptionField = new TextFieldWidget(this.textRenderer, px + PAD, py + DESC_BOX_Y, PANEL_W - 2 * PAD, FIELD_H, Text.empty());
        descriptionField.setMaxLength(256);
        descriptionField.setPlaceholder(Text.translatable("waypointmenu.placeholder.description"));
        //?}
        descriptionField.setText(waypoint.description == null ? "" : waypoint.description);
        addDrawableChild(descriptionField);

        // Bottom bar.
        int saveY = py + DESC_BOX_Y + descH + 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.save"), b -> save())
                .dimensions(px + PAD, saveY, 160, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.cancel"), b -> this.close())
                .dimensions(px + PANEL_W - 172, saveY, 160, BTN_H).build());

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
            dimButton.setMessage(Text.translatable(DIMENSION_KEYS[dimensionIndex]));
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

        context.fill(0, 0, this.width, this.height, 0x80000000);
        int px = panelX();
        int py = panelY();
        Ui.drawFrostedPanel(context, px, py, PANEL_W, panelH());

        // Header title inside the panel, with a subtle divider below it.
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, py + TITLE_Y, 0xFFFFFFFF);
        context.fill(px + PAD, py + DIVIDER_Y, px + PANEL_W - PAD, py + DIVIDER_Y + 1, 0x1EFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.name"), px + PAD, py + NAME_LABEL_Y, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.dimension"), px + PAD, py + DIM_LABEL_Y, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.color"), colorStartX(), py + DIM_LABEL_Y, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.coords"), px + PAD, py + COORDS_LABEL_Y, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.commands"), px + PAD, py + CMD_LABEL_Y, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.field.description"), px + PAD, py + DESC_LABEL_Y, 0xFFAAAAAA, false);

        // Color swatches.
        int sy = colorY();
        for (int i = 0; i < COLORS.length; i++) {
            int sx = colorStartX() + i * (SWATCH_SIZE + SWATCH_GAP);
            context.fill(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE, COLORS[i]);
            if (COLORS[i] == selectedColor) {
                Ui.drawBorder(context, sx - 1, sy - 1, SWATCH_SIZE + 2, SWATCH_SIZE + 2, 0xFFFFFFFF);
            } else {
                Ui.drawBorder(context, sx, sy, SWATCH_SIZE, SWATCH_SIZE, 0x40FFFFFF);
            }
        }

        drawCommandScrollbar(context, px, py);

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

    /** Vertical scrollbar for the command list, shown only when there are >3 commands. */
    private void drawCommandScrollbar(DrawContext context, int px, int py) {
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
        context.fill(trackX, trackTop, trackX + 2, trackTop + trackH, 0x26FFFFFF);
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x80FFFFFF);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (click.button() == 0) {
            int idx = swatchAt(click.x(), click.y());
            if (idx >= 0) {
                selectedColor = COLORS[idx];
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }
    //?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int idx = swatchAt(mouseX, mouseY);
            if (idx >= 0) {
                selectedColor = COLORS[idx];
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Description box handles its own scrollbar; elsewhere the wheel scrolls
        // the command list (replaces the old ▲/▼ buttons).
        if (descriptionField.isMouseOver(mouseX, mouseY)) {
            //? if >=1.21.5 {
            return descriptionField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            //?} else {
            return true; // single-line description box swallows the scroll
            //?}
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
    //?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // 1.20.1 has no horizontal scroll amount and the description box is a
        // single-line field, so it simply swallows the wheel.
        if (descriptionField.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        int px = panelX();
        int py = panelY();
        int cmdTop = py + CMD_ROW0_Y;
        int cmdBottom = py + CMD_ROW0_Y + (VISIBLE_COMMANDS - 1) * ROW_PITCH + FIELD_H;
        if (mouseX >= px && mouseX < px + PANEL_W && mouseY >= cmdTop && mouseY < cmdBottom) {
            commandScroll -= (int) amount;
            clampScroll();
            refreshCommandFields();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    //?}

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        } else {
            super.close();
        }
    }

    private void refreshCommandFields() {
        updating = true;
        for (int i = 0; i < VISIBLE_COMMANDS; i++) {
            int index = commandScroll + i;
            boolean has = index >= 0 && index < commands.size();
            commandFields[i].setText(has ? commands.get(index) : "");
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
            x = Integer.parseInt(xField.getText().trim());
            y = Integer.parseInt(yField.getText().trim());
            z = Integer.parseInt(zField.getText().trim());
        } catch (NumberFormatException e) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.translatable("waypointmenu.message.invalid_number", "X / Y / Z"), false);
            }
            return;
        }

        waypoint.name = nameField.getText().trim();
        if (waypoint.name.isEmpty()) {
            waypoint.name = "Waypoint";
        }
        waypoint.description = descriptionField.getText().trim();
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

        manager.markDirty();
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.translatable("waypointmenu.message.saved"), false);
        }
        this.close();
    }
}
