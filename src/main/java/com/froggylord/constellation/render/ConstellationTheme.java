package com.froggylord.constellation.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/**
 * the mods entire look in one place — palette, panel/card helpers, typography.
 * modelled on cryptkits CryptTheme but constellation-themed (deep space + gold stars).
 * uses nasa public domain nebula imagery where cryptkit used obsidian/purple.
 *
 * palette: near-black space, star-white text, warm gold accent, red for danger.
 */
public final class ConstellationTheme {

    private ConstellationTheme() {}

    // ---- palette ----
    public static final int SPACE     = 0xFF08081A;  // deepest space
    public static final int PANEL     = 0xDD0E0E22;  // semi-transparent panel
    public static final int CARD      = 0xFF14142E;  // card background
    public static final int CARD_ON   = 0xFF1E1E40;  // active/hovered card
    public static final int BORDER    = 0xFF2A2A50;  // card border

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

    // nasa backgrounds
    public static final ResourceLocation BG = ResourceLocation.fromNamespaceAndPath("constellation", "textures/gui/background.png");

    // ---- drawing helpers ----

    /** filled panel with gold accent top line */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        g.fill(x, y, x + w, y + 1, ACCENT);
    }

    /** card for toggleable items — gold left edge when on, muted border when off */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on) {
        g.fill(x, y, x + w, y + h, on ? CARD_ON : CARD);
        if (on) {
            g.fill(x, y, x + 3, y + h, ACCENT);  // left accent bar
            g.fill(x, y, x + w, y + 1, ACCENT);  // top accent bar
        } else {
            g.fill(x, y, x + w, y + 1, BORDER);
        }
    }

    /** small toggle switch */
    public static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        g.fill(x, y, x + 26, y + 14, on ? ACCENT : 0xFF333350);
        int knobX = on ? x + 14 : x + 2;
        g.fill(knobX, y + 2, knobX + 10, y + 12, TEXT);
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
}
