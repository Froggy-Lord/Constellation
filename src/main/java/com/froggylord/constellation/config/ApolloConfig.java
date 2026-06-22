package com.froggylord.constellation.config;

import com.froggylord.constellation.hud.HudPosition;

public class ApolloConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    
    public HudEntry fps       = new HudEntry("FPS", 2, 2, true);
    public HudEntry ping      = new HudEntry("Ping", 2, 10, true);
    public HudEntry tps       = new HudEntry("TPS", 2, 18, false);
    public HudEntry clock     = new HudEntry("Clock", 2, 26, true);
    public HudEntry coords    = new HudEntry("XYZ", 2, 34, true);
    public HudEntry health    = new HudEntry("HP", 50, 90, true);
    public HudEntry mana      = new HudEntry("MN", 62, 90, true);
    public HudEntry defense   = new HudEntry("DEF", 74, 90, true);
    public HudEntry speed     = new HudEntry("SPD", 86, 90, true);
    public HudEntry area      = new HudEntry("Area", 2, 42, false);
    public HudEntry facing    = new HudEntry("Facing", 2, 50, false);
    public HudEntry potions   = new HudEntry("Potions", 2, 82, false);
    public HudEntry powerOrb  = new HudEntry("Orb", 2, 90, false);
    public HudEntry ehp       = new HudEntry("EHP", 38, 90, false);
    public HudEntry overflow  = new HudEntry("Overflow", 56, 86, false);
    public HudEntry skill     = new HudEntry("Skill", 38, 82, false);
    public HudEntry cooldowns = new HudEntry("CDs", 38, 74, false);

    
    public boolean customScoreboard = true;
    public boolean customTabList = true;
    public boolean compactDamage = true;
    public boolean rainbowActionBar = false;

    public static class HudEntry {
        public String label;
        public int x, y;
        public boolean visible;

        public HudEntry() {} 

        public HudEntry(String label, int x, int y, boolean visible) {
            this.label = label; this.x = x; this.y = y; this.visible = visible;
        }

        public HudPosition toPosition() { return HudPosition.of(x, y); }
    }
}
