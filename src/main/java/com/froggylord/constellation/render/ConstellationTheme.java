package com.froggylord.constellation.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

/** shared palette and geometry for constellation screens */
public final class ConstellationTheme {

    private ConstellationTheme() {}

    // ---- palette ----
    public static final int SPACE     = 0xFF08081A;  // deepest space
    public static final int PANEL     = 0xDD0E0E22;  // semi-transparent panel
    public static final int CARD      = 0xFF14142E;  // card background
    public static final int CARD_ON   = 0xFF1E1E40;  // active/hovered card
    public static final int BORDER    = 0xFF2A2A50;  // card border
    public static final int BORDER_SOFT = 0xAA36365E;
    public static final int SURFACE_HOVER = 0xF0252545;

    public static final int ACCENT    = 0xFFFFB830;  // warm gold — active states, toggles
    public static final int ACCENT_BRIGHT = 0xFFFFCC55;
    public static final int ACCENT_DIM    = 0x66996620;

    public static final int RED       = 0xFFE05555;
    public static final int GREEN     = 0xFF46E070;
    public static final int AQUA      = 0xFF52FFFF;

    // text
    public static final int TEXT      = 0xFFF0EDE0;
    public static final int TEXT_DIM  = 0xFFB5B0A5;
    public static final int TEXT_MUTED= 0xFF706C65;
    public static final int TEXT_FAINT= 0xFF4A4640;

    // nasa background texture lives at assets/constellation/textures/gui/background.png
    // wire into screens via guiGraphics.blit() when blit api is confirmed for 26.2

    // ---- drawing helpers ----

    /** two-pixel clipped corners keep surfaces crisp at every gui scale */
    public static void surface(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 2, y, x + w - 2, y + h, fill);
        g.fill(x, y + 2, x + w, y + h - 2, fill);
        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + 1, y + h - 2, border);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, border);
        g.fill(x + 1, y + 1, x + 2, y + 2, border);
        g.fill(x + w - 2, y + 1, x + w - 1, y + 2, border);
        g.fill(x + 1, y + h - 2, x + 2, y + h - 1, border);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, border);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        surface(g, x, y, w, h, PANEL, BORDER_SOFT);
        g.fill(x + 2, y, x + Math.min(w - 2, 50), y + 1, ACCENT);
    }

    /** card for toggleable items — gold left edge when on, muted border when off */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on) {
        surface(g, x, y, w, h, on ? CARD_ON : CARD, on ? ACCENT_DIM : BORDER);
        if (on) {
            g.fill(x, y + 4, x + 2, y + h - 4, ACCENT);
        }
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active) {
        int fill = active ? CARD_ON : hover ? SURFACE_HOVER : CARD;
        int border = active ? ACCENT : hover ? BORDER_SOFT : BORDER;
        surface(g, x, y, w, h, fill, border);
    }

    public static void search(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        surface(g, x, y, w, h, 0xEB101024, focused ? ACCENT : BORDER_SOFT);
        g.fill(x + 7, y + h / 2, x + 10, y + h / 2 + 1, focused ? ACCENT_BRIGHT : TEXT_MUTED);
        g.fill(x + 8, y + h / 2 - 2, x + 9, y + h / 2 + 3, focused ? ACCENT_BRIGHT : TEXT_MUTED);
    }

    public static void pill(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        int width = font.width(text) + 10;
        surface(g, x, y, width, 15, 0xCC101024, color);
        g.text(font, text, x + 5, y + 4, color, false);
    }

    /** small toggle switch — use progress 0..1 for smooth animation */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        toggle(g, x, y, on ? 1f : 0f);
    }
    public static void toggle(GuiGraphicsExtractor g, int x, int y, float progress) {
        // track background fades between off(333350) and on(ACCENT)
        int offCol = 0xFF333350;
        int track = lerpCol(offCol, ACCENT, progress);
        g.fill(x, y, x + 26, y + 14, track);
        // knob slides
        int knobX = Math.round(x + 2 + progress * 12);
        // knob gets a subtle glow when on
        if (progress > 0.5f) {
            int glow = lerpCol(0x00000000, 0x40FFD070, (progress - 0.5f) * 2f);
            g.fill(knobX - 1, y + 1, knobX + 11, y + 13, glow);
        }
        g.fill(knobX, y + 2, knobX + 10, y + 12, TEXT);
    }

    /** glow rectangle — used for card hover effects */
    public static void glow(GuiGraphicsExtractor g, int x, int y, int w, int h, float strength) {
        if (strength <= 0) return;
        int alpha = (int) (strength * 40);
        g.fill(x, y, x + w, y + h, (alpha << 24) | 0xFFCC33);
    }

    /** title text in the panel header area */
    public static void title(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        g.text(font, text, x, y, ACCENT_BRIGHT, false);
    }

    /** section label */
    public static void section(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        g.text(font, text, x, y, TEXT_DIM, false);
    }

    /** dim hint text */
    public static void hint(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        g.text(font, text, x, y, TEXT_MUTED, false);
    }

    static int lerpCol(int a, int b, float t) {
        t = Math.clamp(t, 0f, 1f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
             | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) << 8)
             | Math.round(ab + (bb - ab) * t);
    }

    public static float approach(float value, float target, float delta, float speed) {
        float amount = 1f - (float) Math.exp(-Math.max(0f, delta) * speed);
        return value + (target - value) * amount;
    }

    public static float easeOutCubic(float value) {
        float t = Math.clamp(value, 0f, 1f);
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }
}
