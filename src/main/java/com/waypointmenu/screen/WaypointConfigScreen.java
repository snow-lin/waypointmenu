package com.waypointmenu.screen;

import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.ui.Ui;
//? if >=1.21.9 {
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
//?}
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    private static final int PANEL_H = 212;

    private final WaypointConfig config = WaypointConfig.get();
    private final Screen parent;

    private float opacity;
    private boolean rightClickTeleport;
    private boolean showLabel;
    private boolean crossDimensionTeleport;
    private double textFixedSizeDistance;
    private double diamondRenderDistance;
    private boolean diamondScaleWithDistance;
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
        this.rightClickTeleport = config.rightClickTeleport;
        this.showLabel = config.showLabel;
        this.crossDimensionTeleport = config.crossDimensionTeleport;
        this.textFixedSizeDistance = config.textFixedSizeDistance;
        this.diamondRenderDistance = config.diamondRenderDistance;
        this.diamondScaleWithDistance = config.diamondScaleWithDistance;
        this.keyCombo = config.keyCombo.clone();

        int px = panelX();
        int py = panelY();

        // Label fixed-size distance (beyond which the label keeps a constant
        // on-screen size instead of shrinking).
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeTextFixedSizeDistance(-1.0))
                .dimensions(px + 208, py + 6, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeTextFixedSizeDistance(1.0))
                .dimensions(px + 272, py + 6, 20, 20).build());

        // Diamond opacity.
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeOpacity(-0.05f))
                .dimensions(px + 208, py + 26, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeOpacity(0.05f))
                .dimensions(px + 272, py + 26, 20, 20).build());

        // Diamond render distance (past which the marker is culled).
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> changeDiamondRenderDistance(-16.0))
                .dimensions(px + 208, py + 46, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> changeDiamondRenderDistance(16.0))
                .dimensions(px + 272, py + 46, 20, 20).build());

        // Diamond distance-scaling toggle (affects only the diamond, never the label).
        addDrawableChild(ButtonWidget.builder(
                        diamondScaleToggleText(),
                        b -> {
                            this.diamondScaleWithDistance = !this.diamondScaleWithDistance;
                            b.setMessage(diamondScaleToggleText());
                        })
                .dimensions(px + 208, py + 66, 84, 20).build());

        // Show label toggle.
        addDrawableChild(ButtonWidget.builder(
                        toggleText(),
                        b -> {
                            this.showLabel = !this.showLabel;
                            b.setMessage(toggleText());
                        })
                .dimensions(px + 208, py + 86, 84, 20).build());

        // Cross-dimension teleport toggle (only meaningful when right-click
        // teleport is on; when off, cross-dimension teleport is blocked).
        addDrawableChild(ButtonWidget.builder(
                        crossDimensionTeleportToggleText(),
                        b -> {
                            this.crossDimensionTeleport = !this.crossDimensionTeleport;
                            b.setMessage(crossDimensionTeleportToggleText());
                        })
                .dimensions(px + 208, py + 106, 84, 20).build());

        // Right-click teleport toggle (replaces the old label render distance).
        addDrawableChild(ButtonWidget.builder(
                        teleportToggleText(),
                        b -> {
                            this.rightClickTeleport = !this.rightClickTeleport;
                            b.setMessage(teleportToggleText());
                        })
                .dimensions(px + 208, py + 126, 84, 20).build());

        // In-place keybinding (label + button form): click, then press each key
        // of the combination in turn; Esc commits (or cancels if nothing pressed).
        keybindButton = ButtonWidget.builder(keybindButtonText(), b -> {
                    this.awaitingBind = true;
                    this.pending.clear();
                    keybindButton.setMessage(awaitingText());
                })
                .dimensions(px + 208, py + 146, 84, 20).build();
        addDrawableChild(keybindButton);

        // Open the main waypoint list screen.
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.config.open_main"), b -> {
                    if (this.client != null) {
                        this.client.setScreen(new WaypointListScreen());
                    }
                })
                .dimensions(px + 12, py + 166, PANEL_W - 24, 20).build());

        // Bottom bar.
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.save"), b -> save())
                .dimensions(px + 12, py + PANEL_H - 26, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("waypointmenu.button.cancel"), b -> this.close())
                .dimensions(px + PANEL_W - 152, py + PANEL_H - 26, 140, 20).build());
    }

    private Text toggleText() {
        return Text.translatable(this.showLabel ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Text diamondScaleToggleText() {
        return Text.translatable(this.diamondScaleWithDistance ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Text crossDimensionTeleportToggleText() {
        return Text.translatable(this.crossDimensionTeleport ? "waypointmenu.config.on" : "waypointmenu.config.off");
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

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyInput input) {
        if (this.awaitingBind) {
            return handleKey(input.key());
        }
        return super.keyPressed(input);
    }
    //?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.awaitingBind) {
            return handleKey(keyCode);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    /** Shared key handling for the era-specific keyPressed signatures. */
    private boolean handleKey(int keycode) {
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

    //? if >=1.21.9 {
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
    //?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.awaitingBind && button == 0) {
            boolean onKeybind = isOverKeybindButton(mouseX, mouseY);
            commitPending();
            if (onKeybind) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    private void changeOpacity(float delta) {
        this.opacity = WaypointConfig.clampOpacity(this.opacity + delta);
    }

    private Text teleportToggleText() {
        return Text.translatable(this.rightClickTeleport ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private void changeTextFixedSizeDistance(double delta) {
        this.textFixedSizeDistance = WaypointConfig.clampTextFixedDistance(this.textFixedSizeDistance + delta);
    }

    private void changeDiamondRenderDistance(double delta) {
        this.diamondRenderDistance = WaypointConfig.clampDiamondRenderDistance(this.diamondRenderDistance + delta);
    }

    private void save() {
        config.highlightOpacity = this.opacity;
        config.rightClickTeleport = this.rightClickTeleport;
        config.showLabel = this.showLabel;
        config.textFixedSizeDistance = this.textFixedSizeDistance;
        config.diamondRenderDistance = this.diamondRenderDistance;
        config.diamondScaleWithDistance = this.diamondScaleWithDistance;
        config.crossDimensionTeleport = this.crossDimensionTeleport;
        config.keyCombo = this.keyCombo;
        config.save();
        this.close();
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        // Nudge the panel (and its title) below the exact centre so the title
        // text clears the top edge without the panel overflowing the bottom;
        // clamp so the taller panel stays on-screen in a short window.
        int y = (this.height - PANEL_H) / 2 + 10;
        return Math.max(12, Math.min(y, this.height - PANEL_H - 2));
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
        Ui.drawFrostedPanel(context, px, py, PANEL_W, PANEL_H);
        // Title sits 8px above the panel; nudge it up a further 12px total
        // gap so it reads as a distinct header rather than hugging the panel.
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, py - 20, 0xFFFFFFFF);

        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.text_size_distance"), px + 12, py + 12, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.opacity"), px + 12, py + 32, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.diamond_distance"), px + 12, py + 52, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.diamond_scale_distance"), px + 12, py + 72, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.show_label"), px + 12, py + 92, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.cross_dimension_teleport"), px + 12, py + 112, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.right_click_teleport"), px + 12, py + 132, 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, Text.translatable("waypointmenu.config.keybind"), px + 12, py + 152, 0xFFAAAAAA, false);

        String textSizeText = String.format("%.0f", this.textFixedSizeDistance);
        context.drawText(this.textRenderer, Text.literal(textSizeText), px + 268 - this.textRenderer.getWidth(textSizeText), py + 12, 0xFFFFFFFF, false);
        String opacityText = String.format("%.0f%%", this.opacity * 100);
        context.drawText(this.textRenderer, Text.literal(opacityText), px + 268 - this.textRenderer.getWidth(opacityText), py + 32, 0xFFFFFFFF, false);
        String diamondText = String.format("%.0f", this.diamondRenderDistance);
        context.drawText(this.textRenderer, Text.literal(diamondText), px + 268 - this.textRenderer.getWidth(diamondText), py + 52, 0xFFFFFFFF, false);

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
