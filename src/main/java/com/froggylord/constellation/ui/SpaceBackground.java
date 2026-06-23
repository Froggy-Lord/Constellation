package com.froggylord.constellation.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Random;

/**
 * procedural animated space background — starfield + nebula gradient.
 * no external assets. stars twinkle, nebula slowly drifts.
 * rendered behind hub/config screens.
 *
 * public domain imagery from NASA is the same concept — this just
 * generates it procedurally so we don't bundle 20MB of files.
 */
public final class SpaceBackground {

    private static final int STAR_COUNT = 200;
    private static final float[] starX = new float[STAR_COUNT];
    private static final float[] starY = new float[STAR_COUNT];
    private static final float[] starBright = new float[STAR_COUNT];
    private static final float[] starSize = new float[STAR_COUNT];
    private static final long[] starPhase = new long[STAR_COUNT];
    private static final Random RNG = new Random(42); // deterministic so it doesn't jump
    private static boolean init = false;

    static {
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = RNG.nextFloat();
            starY[i] = RNG.nextFloat();
            starBright[i] = 0.3f + RNG.nextFloat() * 0.7f;
            starSize[i] = 0.5f + RNG.nextFloat() * 2.0f;
            starPhase[i] = RNG.nextInt(3000);
        }
    }

    /** render the full background. call at start of screen render. */
    public static void render(GuiGraphicsExtractor g, int screenW, int screenH, float partialTick) {
        long now = System.currentTimeMillis();

        // deep space gradient — purple-navy to near-black
        g.fill(0, 0, screenW, screenH, 0xFF0A0A1A);
        // subtle nebula bands
        float drift = (now % 120_000) / 120_000f; // 2-minute cycle
        int nebula1 = lerpColour(0x20101030, 0x20081028, drift);
        int nebula2 = lerpColour(0x20081028, 0x20101030, (drift + 0.5f) % 1f);
        g.fill(0, screenH / 3, screenW, screenH * 2 / 3, nebula1);
        g.fill(0, 0, screenW, screenH / 2, nebula2);

        // stars — simple pixel dots with twinkle
        for (int i = 0; i < STAR_COUNT; i++) {
            int sx = (int) (starX[i] * screenW);
            int sy = (int) (starY[i] * screenH);
            if (sx < 0 || sx >= screenW || sy < 0 || sy >= screenH) continue;
            float twinkle = (float) (0.4 + 0.6 * Math.sin((now + starPhase[i]) / 800.0));
            float bright = starBright[i] * twinkle;
            int alpha = (int) (bright * 200);
            int col = (alpha << 24) | 0xCCCCFF;
            int s = Math.round(starSize[i]);
            g.fill(sx, sy, sx + s, sy + s, col);
            // occasional brighter one
            if (i % 13 == 0 && twinkle > 0.85) {
                g.fill(sx - 1, sy - 1, sx + s + 1, sy + s + 1, 0xFFEEEEDD);
            }
        }
    }

    private static int lerpColour(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b2 = Math.round(ab + (bb - ab) * t);
        int al = Math.round(aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | b2;
    }
}
