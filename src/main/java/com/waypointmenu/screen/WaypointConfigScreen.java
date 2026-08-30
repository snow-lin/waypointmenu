package com.waypointmenu.screen;

import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.ui.Ui;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game configuration screen, reached from Mod Menu. Edits are staged in
 * local fields and only written to disk when the save button is pressed.
 */
public class WaypointConfigScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 168;

    private final WaypointConfig config = WaypointConfig.get();
    private final Screen parent;

    private float opacity;
    private int distance;
    private boolean showLabel;
    private int[] keyCombo;

    private ButtonWidget keybindButton;
    private boolean awaitingBind = false;
    private final List<Integer> pending = new ArrayList<>();

    public WaypointConfigScreen(Screen parent) {
        super(Text.translatable("screen.waypointmenu.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.opacity = config.highlightOpacity;
        this.distance = config.labelDistance;
        this.showLabel = config.showLabel;
        this.keyCombo = config.keyCombo.clone();

        int px = panelX();
        int py = panelY();

        // Highlight opacity.
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeOpacity(-0.05f))
                .dimensions(px + 208, py + 16, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeOpacity(0.05f))
                .dimensions(px + 272, py + 16, 20, 20).build());

        // Label render distance.
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeDistance(-64))
                .dimensions(px + 208, py + 40, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeDistance(64))
                .dimensions(px + 272, py + 40, 20, 20).build());

        // Show label toggle.
        addDrawableChild(ButtonWidget.builder(
                        toggleText(),
                        b -> {
                            this.showLabel = !this.showLabel;
                            b.setMessage(toggleText());
                        })
                .dimensions(px + 208, py + 64, 84, 20).build());

        // In-place keybinding (label + button form): click, then press each key
        // of the combination in turn; Esc commits (or cancels if nothing pressed).
        keybindButton = ButtonWidget.builder(keybindButtonText(), b -> {
                    this.awaitingBind = true;
                    this.pending.clear();
                    keybindButton.setMessage(awaitingText());
                })
                .dimensions(px + 188, py + 88, 104, 20).build();
        addDrawableChild(keybindButton);

        // Open the main waypoint list screen.
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.config.open_main"), b -> {
                    if (this.client != null) {
                        this.client.setScreen(new WaypointListScreen());
                    }
                })
                .dimensions(px + 12, py + 112, PANEL_W - 24, 20).build());

        // Bottom bar.
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.save"), b -> save())
                .dimensions(px + 12, py + PANEL_H - 32, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.cancel"), b -> this.close())
                .dimensions(px + PANEL_W - 152, py + PANEL_H - 32, 140, 20).build());
    }

    private Text toggleText() {
        return Text.translatable(this.showLabel ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Text keybindButtonText() {
        return Text.literal(comboString(this.keyCombo));
    }

    /** Formats a key combination like "G+J" using localized key names. */
    private static String comboString(int[] combo) {
        if (combo == null || combo.length == 0) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < combo.length; i++) {
            if (i > 0) {
                sb.append("+");
            }
            sb.append(keyName(combo[i]));
        }
        return sb.toString();
    }

    private static String keyName(int keycode) {
        return InputUtil.Type.KEYSYM.createFromCode(keycode).getLocalizedText().getString();
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private Text awaitingText() {
        return Text.translatable("waypointmenu.config.keybind_awaiting");
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.awaitingBind) {
            int keycode = input.key();
            if (keycode == InputUtil.GLFW_KEY_ESCAPE) {
                // Esc clears the binding (unbind).
                this.awaitingBind = false;
                this.pending.clear();
                this.keyCombo = new int[0];
                keybindButton.setMessage(keybindButtonText());
                return true;
            }
            if (keycode > 0 && !this.pending.contains(keycode)) {
                this.pending.add(keycode);
                keybindButton.setMessage(Text.literal(comboString(toIntArray(this.pending))));
            }
            return true;
        }
        return super.keyPressed(input);
    }

    /** Ends recording and commits the captured combination (if any). */
    private void commitPending() {
        if (!this.awaitingBind) {
            return;
        }
        this.awaitingBind = false;
        if (!this.pending.isEmpty()) {
            this.keyCombo = toIntArray(this.pending);
        }
        this.pending.clear();
        keybindButton.setMessage(keybindButtonText());
    }

    private boolean isOverKeybindButton(double x, double y) {
        return keybindButton != null
                && x >= keybindButton.getX() && x < keybindButton.getX() + keybindButton.getWidth()
                && y >= keybindButton.getY() && y < keybindButton.getY() + keybindButton.getHeight();
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (this.awaitingBind && click.button() == 0) {
            // Losing focus ends recording; commit the captured combination.
            boolean onKeybind = isOverKeybindButton(click.x(), click.y());
            commitPending();
            if (onKeybind) {
                return true; // swallow so the button doesn't immediately re-enter recording
            }
            // Fall through so the click also reaches the widget underneath.
        }
        return super.mouseClicked(click, bl);
    }

    private void changeOpacity(float delta) {
        this.opacity = WaypointConfig.clampOpacity(this.opacity + delta);
    }

    private void changeDistance(int delta) {
        this.distance = WaypointConfig.clampDistance(this.distance + delta);
    }

    private void save() {
        config.highlightOpacity = this.opacity;
        config.labelDistance = this.distance;
        config.showLabel = this.showLabel;
        config.keyCombo = this.keyCombo;
        config.save();
        this.close();
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x80000000);
        int px = panelX();
        int py = panelY();
        Ui.drawFrostedPanel(context, px, py, PANEL_W, PANEL_H);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, py - 12, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.opacity"), px + 12, py + 22, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.distance"), px + 12, py + 46, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.show_label"), px + 12, py + 70, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.keybind"), px + 12, py + 94, 0xFFAAAAAA, false);

        String opacityText = String.format("%.0f%%", this.opacity * 100);
        context.drawText(this.textRenderer, Text.literal(opacityText), px + 268 - this.textRenderer.getWidth(opacityText), py + 22, 0xFFFFFFFF, false);
        String distText = String.valueOf(this.distance);
        context.drawText(this.textRenderer, Text.literal(distText), px + 268 - this.textRenderer.getWidth(distText), py + 46, 0xFFFFFFFF, false);

        super.render(context, mouseX, mouseY, delta);
    }

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
}
