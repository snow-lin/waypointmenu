package com.waypointmenu.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Shared drawing helpers for the frosted-glass (毛玻璃) UI look.
 *
 * <p>{@link #drawRoundedRect} fills row by row with no overlapping draws so
 * that semi-transparent colors blend exactly once. Overlapping fills would
 * double-blend and leave visible darker "cores", which read as extra
 * misaligned background layers.</p>
 */
public final class Ui {
    private static final int PANEL_RADIUS = 8;
    private static final int ROW_RADIUS = 4;

    private Ui() {
    }

    /** Draws the frosted-glass panel: a single translucent rounded rectangle. */
    public static void drawFrostedPanel(DrawContext ctx, int x, int y, int w, int h) {
        drawRoundedRect(ctx, x, y, x + w, y + h, PANEL_RADIUS, 0xC8141820);
    }

    /** Draws a rounded list-row background. */
    public static void drawRow(DrawContext ctx, int x, int y, int w, int h, boolean hovered) {
        drawRoundedRect(ctx, x, y, x + w, y + h, ROW_RADIUS, hovered ? 0x28FFFFFF : 0x12FFFFFF);
    }

    /** Draws a rounded square action button. */
    public static void drawButton(DrawContext ctx, int x, int y, int size, boolean hovered) {
        drawRoundedRect(ctx, x, y, x + size, y + size, ROW_RADIUS, hovered ? 0xFF34343E : 0xFF22222C);
    }

    /** Draws a 1px border around the given rectangle. */
    public static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** Fills a rounded rectangle with a single pass per pixel row. */
    public static void drawRoundedRect(DrawContext ctx, int x, int y, int x1, int y1, int radius, int color) {
        int w = x1 - x;
        int h = y1 - y;
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x, y, x1, y1, color);
            return;
        }
        for (int row = 0; row < h; row++) {
            int dist = Math.min(row, h - 1 - row);
            int inset = 0;
            if (dist < r) {
                int d = r - dist;
                inset = r - (int) Math.round(Math.sqrt((double) r * r - (double) d * d));
            }
            ctx.fill(x + inset, y + row, x1 - inset, y + row + 1, color);
        }
    }
}
