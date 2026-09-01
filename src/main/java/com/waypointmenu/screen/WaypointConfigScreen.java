package com.waypointmenu.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.waypointmenu.ClientCompat;
import com.waypointmenu.config.WaypointConfig;
import com.waypointmenu.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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

    private Button keybindButton;
    private boolean awaitingBind = false;
    private final List<Integer> pending = new ArrayList<>();

    public WaypointConfigScreen(Screen parent) {
        super(Component.translatable("screen.waypointmenu.config.title"));
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
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeTextFixedSizeDistance(-1.0))
                .bounds(px + 208, py + 6, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeTextFixedSizeDistance(1.0))
                .bounds(px + 272, py + 6, 20, 20).build());

        // Diamond opacity.
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeOpacity(-0.05f))
                .bounds(px + 208, py + 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeOpacity(0.05f))
                .bounds(px + 272, py + 26, 20, 20).build());

        // Diamond render distance (past which the marker is culled).
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeDiamondRenderDistance(-16.0))
                .bounds(px + 208, py + 46, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeDiamondRenderDistance(16.0))
                .bounds(px + 272, py + 46, 20, 20).build());

        // Diamond distance-scaling toggle (affects only the diamond, never the label).
        addRenderableWidget(Button.builder(
                        diamondScaleToggleText(),
                        b -> {
                            this.diamondScaleWithDistance = !this.diamondScaleWithDistance;
                            b.setMessage(diamondScaleToggleText());
                        })
                .bounds(px + 208, py + 66, 84, 20).build());

        // Show label toggle.
        addRenderableWidget(Button.builder(
                        toggleText(),
                        b -> {
                            this.showLabel = !this.showLabel;
                            b.setMessage(toggleText());
                        })
                .bounds(px + 208, py + 86, 84, 20).build());

        // Cross-dimension teleport toggle (only meaningful when right-click
        // teleport is on; when off, cross-dimension teleport is blocked).
        addRenderableWidget(Button.builder(
                        crossDimensionTeleportToggleText(),
                        b -> {
                            this.crossDimensionTeleport = !this.crossDimensionTeleport;
                            b.setMessage(crossDimensionTeleportToggleText());
                        })
                .bounds(px + 208, py + 106, 84, 20).build());

        // Right-click teleport toggle (replaces the old label render distance).
        addRenderableWidget(Button.builder(
                        teleportToggleText(),
                        b -> {
                            this.rightClickTeleport = !this.rightClickTeleport;
                            b.setMessage(teleportToggleText());
                        })
                .bounds(px + 208, py + 126, 84, 20).build());

        // In-place keybinding (label + button form): click, then press each key
        // of the combination in turn; Esc commits (or cancels if nothing pressed).
        keybindButton = Button.builder(keybindButtonText(), b -> {
                    this.awaitingBind = true;
                    this.pending.clear();
                    keybindButton.setMessage(awaitingText());
                })
                .bounds(px + 208, py + 146, 84, 20).build();
        addRenderableWidget(keybindButton);

        // Open the main waypoint list screen.
        addRenderableWidget(Button.builder(Component.translatable("waypointmenu.config.open_main"), b -> {
                    if (this.minecraft != null) {
                        ClientCompat.setScreen(this.minecraft, new WaypointListScreen());
                    }
                })
                .bounds(px + 12, py + 166, PANEL_W - 24, 20).build());

        // Bottom bar.
        addRenderableWidget(Button.builder(Component.translatable("waypointmenu.button.save"), b -> save())
                .bounds(px + 12, py + PANEL_H - 26, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("waypointmenu.button.cancel"), b -> this.onClose())
                .bounds(px + PANEL_W - 152, py + PANEL_H - 26, 140, 20).build());
    }

    private Component toggleText() {
        return Component.translatable(this.showLabel ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Component diamondScaleToggleText() {
        return Component.translatable(this.diamondScaleWithDistance ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Component crossDimensionTeleportToggleText() {
        return Component.translatable(this.crossDimensionTeleport ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Component teleportToggleText() {
        return Component.translatable(this.rightClickTeleport ? "waypointmenu.config.on" : "waypointmenu.config.off");
    }

    private Component keybindButtonText() {
        return Component.literal(comboString(this.keyCombo));
    }

    private Component awaitingText() {
        return Component.translatable("waypointmenu.config.keybind_awaiting");
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
        return InputConstants.Type.KEYSYM.getOrCreate(keycode).getDisplayName().getString();
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.awaitingBind) {
            return handleKey(event.key());
        }
        return super.keyPressed(event);
    }

    /** Shared key handling for the keyPressed signature. */
    private boolean handleKey(int keycode) {
        if (keycode == InputConstants.KEY_ESCAPE) {
            // Esc clears the binding (unbind).
            this.awaitingBind = false;
            this.pending.clear();
            this.keyCombo = new int[0];
            keybindButton.setMessage(keybindButtonText());
            return true;
        }
        if (keycode > 0 && !this.pending.contains(keycode)) {
            this.pending.add(keycode);
            keybindButton.setMessage(Component.literal(comboString(toIntArray(this.pending))));
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (this.awaitingBind && event.button() == 0) {
            // Losing focus ends recording; commit the captured combination.
            boolean onKeybind = isOverKeybindButton(event.x(), event.y());
            commitPending();
            if (onKeybind) {
                return true; // swallow so the button doesn't immediately re-enter recording
            }
            // Fall through so the click also reaches the widget underneath.
        }
        return super.mouseClicked(event, bl);
    }

    private void changeOpacity(float delta) {
        this.opacity = WaypointConfig.clampOpacity(this.opacity + delta);
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
        this.onClose();
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
    public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, 0x80000000);
        int px = panelX();
        int py = panelY();
        Ui.drawFrostedPanel(gui, px, py, PANEL_W, PANEL_H);
        // Title sits 8px above the panel; nudge it up a further 12px total
        // gap so it reads as a distinct header rather than hugging the panel.
        Ui.drawCenteredText(gui, this.font, this.title, this.width / 2, py - 20, 0xFFFFFFFF);

        gui.text(this.font, Component.translatable("waypointmenu.config.text_size_distance"), px + 12, py + 12, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.opacity"), px + 12, py + 32, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.diamond_distance"), px + 12, py + 52, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.diamond_scale_distance"), px + 12, py + 72, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.show_label"), px + 12, py + 92, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.cross_dimension_teleport"), px + 12, py + 112, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.right_click_teleport"), px + 12, py + 132, 0xFFAAAAAA, false);
        gui.text(this.font, Component.translatable("waypointmenu.config.keybind"), px + 12, py + 152, 0xFFAAAAAA, false);

        String textSizeText = String.format("%.0f", this.textFixedSizeDistance);
        gui.text(this.font, Component.literal(textSizeText), px + 268 - this.font.width(textSizeText), py + 12, 0xFFFFFFFF, false);
        String opacityText = String.format("%.0f%%", this.opacity * 100);
        gui.text(this.font, Component.literal(opacityText), px + 268 - this.font.width(opacityText), py + 32, 0xFFFFFFFF, false);
        String diamondText = String.format("%.0f", this.diamondRenderDistance);
        gui.text(this.font, Component.literal(diamondText), px + 268 - this.font.width(diamondText), py + 52, 0xFFFFFFFF, false);

        super.extractRenderState(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            ClientCompat.setScreen(this.minecraft, parent);
        } else {
            super.onClose();
        }
    }
}
