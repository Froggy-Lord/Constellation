package com.froggylord.constellation.ui;

import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.Random;

public final class SpaceBackground {

    private static final Identifier BG =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/background.png");
    private static final Identifier BG_ALT =
        Identifier.fromNamespaceAndPath("constellation", "textures/gui/bg_config.png");

    // shooting stars
    private static long nextShootingStar = 0;
    private static float shootX, shootY, shootDX, shootDY, shootLen;
    private static long shootAt;
    private static final Random RNG = new Random(42);

    public static void render(GuiGraphicsExtractor g, int w, int h, float delta) {
        g.blit(RenderPipelines.GUI_TEXTURED, BG, 0, 0, 0, 0, w, h, 2048, 2048);
        g.fill(0, 0, w, h, 0xAA080814);
        shootStars(g, w, h);
    }

    public static void renderConfig(GuiGraphicsExtractor g, int w, int h, float delta) {
        g.blit(RenderPipelines.GUI_TEXTURED, BG_ALT, 0, 0, 0, 0, w, h, 2048, 2048);
        g.fill(0, 0, w, h, 0xAA080814);
        shootStars(g, w, h);
    }

    private static void shootStars(GuiGraphicsExtractor g, int w, int h) {
        long now = System.currentTimeMillis();
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
            for (int si = 0; si < shootLen; si++) {
                int sa = (int) ((1f - (float) si / shootLen) * alpha);
                int px = sx - (int) (shootDX * si * 0.2f);
                int py = sy - (int) (shootDY * si * 0.2f);
                if (px > 0 && px < w && py > 0 && py < h && sa > 5)
                    g.fill(px, py, px + 1 + (int)(fade * 2), py + 1, (sa << 24) | 0xFFFFEECC);
            }
        }
    }

    public static void fadeIn(GuiGraphicsExtractor g, int w, int h, long openTime) {
        long age = System.currentTimeMillis() - openTime;
        if (age >= 400) return;
        float alpha = 1f - (float) age / 400f;
        g.fill(0, 0, w, h, ((int)(alpha * 255) << 24) | 0x000000);
    }
}
