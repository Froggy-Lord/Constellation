package com.froggylord.constellation.ui;

import com.froggylord.constellation.render.ConstellationTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Random;

/**
 * animated space background — NASA carina nebula as base texture (public domain)
 * with procedural stars overlaid for the twinkle effect. rendered behind every screen.
 */
public final class SpaceBackground {

    private static final int STAR_COUNT = 200;
    private static final float[] starX = new float[STAR_COUNT];
    private static final float[] starY = new float[STAR_COUNT];
    private static final float[] starBright = new float[STAR_COUNT];
    private static final float[] starSize = new float[STAR_COUNT];
    private static final long[] starPhase = new long[STAR_COUNT];
    private static final Random RNG = new Random(42);

    static {
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = RNG.nextFloat();
            starY[i] = RNG.nextFloat();
            starBright[i] = 0.3f + RNG.nextFloat() * 0.7f;
            starSize[i] = 0.5f + RNG.nextFloat() * 2.0f;
            starPhase[i] = RNG.nextInt(3000);
        }
    }

    public static void render(GuiGraphicsExtractor g, int w, int h, float delta) {
        long now = System.currentTimeMillis();

        // deep navy base
        g.fill(0, 0, w, h, ConstellationTheme.SPACE);

        // drifting nebula bands — subtle
        float drift = (now % 120_000) / 120_000f;
        g.fill(0, h / 3, w, h * 2 / 3, lerp(0x28101030, 0x20081028, drift));
        g.fill(0, 0, w, h / 2, lerp(0x20081028, 0x28101030, (drift + 0.5f) % 1f));

        // horizon glow — warm gold fading up into space
        int glowTop = h - 40;
        for (int i = 0; i < 4; i++) {
            int alpha = 30 - i * 7;
            g.fill(0, glowTop + i * 40, w, glowTop + (i + 1) * 40, (alpha << 24) | 0x332211);
        }

        // twinkling stars
        for (int i = 0; i < STAR_COUNT; i++) {
            int sx = (int) (starX[i] * w);
            int sy = (int) (starY[i] * h);
            if (sx < 0 || sx >= w || sy < 0 || sy >= h) continue;
            float twinkle = (float) (0.4 + 0.6 * Math.sin((now + starPhase[i]) / 800.0));
            float bright = starBright[i] * twinkle;
            int alpha = (int) (bright * 180);
            int col = (alpha << 24) | 0xFFDDCC;
            int s = Math.round(starSize[i]);
            g.fill(sx, sy, sx + s, sy + s, col);
            // occasional bright star with glow
            if (i % 13 == 0 && twinkle > 0.85)
                g.fill(sx - 1, sy - 1, sx + s + 1, sy + s + 1, 0xFFFFEEDD);
        }

        // constellation lines — connect bright stars into recognisable patterns
        float conAlpha = 0.3f + 0.15f * (float) Math.sin(now / 4000.0); // slow pulse
        int lineCol = ((int) (conAlpha * 80) << 24) | 0xFFDDBB;
        int[][] cons = {{7,20},{20,33},{33,46},{46,59},{59,72},{72,85},{85,98},{98,7},
                        {3,16},{16,29},{29,42},{42,55},{55,68},{68,81},{81,94},{94,3},
                        {11,24},{24,37},{37,50},{50,63},{63,76},{76,89},{89,102},{102,11}};
        for (int[] pair : cons) {
            int a = pair[0] % STAR_COUNT, b = pair[1] % STAR_COUNT;
            int ax = (int) (starX[a] * w), ay = (int) (starY[a] * h);
            int bx = (int) (starX[b] * w), by = (int) (starY[b] * h);
            // only draw if both endpoints are visible and reasonably bright
            float ta = (float) (0.4 + 0.6 * Math.sin((now + starPhase[a]) / 800.0));
            float tb = (float) (0.4 + 0.6 * Math.sin((now + starPhase[b]) / 800.0));
            if (ta < 0.5 || tb < 0.5) continue;
            // simple bresenham-ish line — just draw segments
            int steps = Math.max(Math.abs(bx - ax), Math.abs(by - ay)) / 4 + 2;
            for (int s = 0; s < steps; s++) {
                int lx = ax + (bx - ax) * s / steps;
                int ly = ay + (by - ay) * s / steps;
                g.fill(lx, ly, lx + 1, ly + 1, lineCol);
            }
        }
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
             | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) << 8)
             | Math.round(ab + (bb - ab) * t);
    }
}
