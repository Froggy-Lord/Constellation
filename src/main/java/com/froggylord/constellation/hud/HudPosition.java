package com.froggylord.constellation.hud;

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
