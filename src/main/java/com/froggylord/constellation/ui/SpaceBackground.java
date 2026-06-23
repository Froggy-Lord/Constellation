package com.froggylord.constellation.ui;

import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * renders the nasa carina nebula as a screen background (public domain).
 * 26.2 uses blitSprite with RenderPipelines.GUI_TEXTURED for texture blits.
 *
 * all the twinkling stars and constellation lines? those are in the
 * actual nasa photo — the real thing looks better than anything procedural.
 */
public final class SpaceBackground {

    private static final Identifier BG =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/background.png"); // carina
    private static final Identifier BG_ALT =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/bg_config.png");  // helix

    private static long nextShootingStar = 0;
    private static float shootX, shootY, shootDX, shootDY, shootLen;
    private static long shootAt;
    private static final java.util.Random RNG = new java.util.Random(42);

    public static void render(GuiGraphicsExtractor g, int w, int h, float delta) {
        renderWith(g, w, h, delta, BG);
    }
    public static void renderConfig(GuiGraphicsExtractor g, int w, int h, float delta) {
        renderWith(g, w, h, delta, BG_ALT);
    }

    static void renderWith(GuiGraphicsExtractor g, int w, int h, float delta, Identifier bg) {
        long now = System.currentTimeMillis();

        // slow drift — pan across the 1024px nebula texture over ~3 minutes
        // so the background is never static but movement is barely noticeable
        int texSize = 1024;
        float panSpeed = texSize / 180_000f; // px per ms — full pan in 3 min
        int viewW = Math.min(texSize, (int)(w * 1.2f)); // show slightly more than screen
        int viewH = Math.min(texSize, (int)(h * 1.2f));
        float u0 = ((now * panSpeed) % (texSize - viewW));
        float v0 = (float) Math.sin(now / 30000.0) * 20 + (texSize - viewH) / 2f;

        // render the drifting window of the nebula
        g.blit(RenderPipelines.GUI_TEXTURED, bg,
            0, 0, (int)u0, (int)v0, w, h, viewW, viewH);

        // subtle dark overlay so ui is readable
        int overlayAlpha = 120;
        g.fill(0, 0, w, h, (overlayAlpha << 24) | 0x0A0A18);

        // rare shooting star — tiny streak across the photo
        if (now > nextShootingStar) {
            shootX = RNG.nextFloat() * 0.8f;
            shootY = RNG.nextFloat() * 0.5f;
            shootDX = (RNG.nextFloat() - 0.2f) * w / 200f;
            shootDY = (RNG.nextFloat() * 0.6f + 0.3f) * h / 120f;
            shootLen = 30 + RNG.nextFloat() * 50;
            shootAt = now;
            nextShootingStar = now + 10000 + RNG.nextInt(25000);
        }
        long shootAge = now - shootAt;
        if (shootAge < 1000) {
            float fade = 1f - (float) shootAge / 1000f;
            int sx = (int) (shootX * w + shootDX * shootAge * 0.3f);
            int sy = (int) (shootY * h + shootDY * shootAge * 0.3f);
            int alpha = (int) (fade * 160);
            int col = (alpha << 24) | 0xFFFFEECC;
            for (int si = 0; si < shootLen; si++) {
                int sa = (int) ((1f - (float) si / shootLen) * alpha);
                int px = sx - (int) (shootDX * si * 0.2f);
                int py = sy - (int) (shootDY * si * 0.2f);
                if (px > 0 && px < w && py > 0 && py < h && sa > 5)
                    g.fill(px, py, px + 1 + (int)(fade * 2), py + 1, (sa << 24) | 0xFFFFEECC);
            }
        }
    }

    /** fade-in overlay — black→clear over ~400ms */
    public static void fadeIn(GuiGraphicsExtractor g, int w, int h, long openTime) {
        long age = System.currentTimeMillis() - openTime;
        if (age >= 400) return;
        float alpha = 1f - (float) age / 400f;
        g.fill(0, 0, w, h, ((int)(alpha * 255) << 24) | 0x000000);
    }
}
