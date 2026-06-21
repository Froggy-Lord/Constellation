package com.froggylord.constellation.hud;

/**
 * Screen position for a HUD element. Stored as 0-100 percentage of screen width/height.
 */
public record HudPosition(int x, int y) {

    public static HudPosition of(int x, int y) {
        return new HudPosition(x, y);
    }

    public HudPosition offset(int dx, int dy) {
        return new HudPosition(x + dx, y + dy);
    }

    @Override
    public String toString() {
        return x + "," + y;
    }
}
