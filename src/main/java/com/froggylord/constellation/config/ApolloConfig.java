package com.froggylord.constellation.config;

import com.froggylord.constellation.hud.HudPosition;

/**
 * Config for the Apollo constellation (core HUD).
 */
public class ApolloConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    // hud element positions — label coords in screen %, 0-100
    public HudEntry fps       = new HudEntry("FPS", 2, 2, true);
    public HudEntry ping      = new HudEntry("Ping", 2, 10, true);
    public HudEntry tps       = new HudEntry("TPS", 2, 18, false);
    public HudEntry clock     = new HudEntry("Clock", 2, 26, true);
    public HudEntry coords    = new HudEntry("XYZ", 2, 34, true);
    public HudEntry health    = new HudEntry("HP", 50, 90, true);
    public HudEntry mana      = new HudEntry("MN", 62, 90, true);
    public HudEntry defense   = new HudEntry("DEF", 74, 90, true);
    public HudEntry speed     = new HudEntry("SPD", 86, 90, true);

    // feature toggles
    public boolean customScoreboard = true;
    public boolean customTabList = true;
    public boolean compactDamage = true;
    public boolean rainbowActionBar = false;

    /** Simple hud element entry: label + screen% position + enabled toggle */
    public static class HudEntry {
        public String label;
        public int x, y;
        public boolean visible;

        public HudEntry() {} // gson

        public HudEntry(String label, int x, int y, boolean visible) {
            this.label = label; this.x = x; this.y = y; this.visible = visible;
        }

        public HudPosition toPosition() { return HudPosition.of(x, y); }
    }
}
