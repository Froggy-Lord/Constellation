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

    // animation frames at assets/constellation/textures/gui/anim/ — 8 subtle wave-distorted variants
    // blit has no alpha blending so we can't overlay them cleanly. the drift+zoom+shooting stars
    // provide the animation. frames kept as assets for future use when blit supports alpha.

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

        // smooth drift animation across the 2048px nebula
        int texSize = 2048;
        long driftMs = 240_000; // full horizontal pan over 4 minutes
        float u0 = (float) ((now % driftMs) / (double) driftMs) * (texSize - w);
        float v0 = (float) (Math.sin(now / 25000.0) * 40 + (texSize - h) / 2.0);
        // breathing zoom — subtle 5% scale oscillation over ~8 seconds
        float zoom = 1.0f + (float) Math.sin(now / 8000.0) * 0.03f;
        int srcW = (int) (w / zoom);
        int srcH = (int) (h / zoom);
        int srcX = (int) u0 + (w - srcW) / 2;
        int srcY = (int) v0 + (h - srcH) / 2;

        g.blit(RenderPipelines.GUI_TEXTURED, bg,
            0, 0, Math.clamp(srcX, 0, texSize - srcW), Math.clamp(srcY, 0, texSize - srcH),
            w, h, srcW, srcH);

        // dark overlay so ui is readable over the nebula
        g.fill(0, 0, w, h, 0x90080814);

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
