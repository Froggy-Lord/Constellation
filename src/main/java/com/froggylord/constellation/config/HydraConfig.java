package com.froggylord.constellation.config;

/** Config for the Hydra constellation (fishing). */
public class HydraConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean seaCreatureAlerts = true;
    public boolean rareSeaCreatureAlert = true;
    public boolean rareSeaCreaturePartyPing = false;
    public boolean hideOtherHooks = false;
    public boolean trophyFishTracker = true;
    public boolean goldenFishTimer = true;  // golden fish
    public boolean barnTimer = true;         // barn timer
    public boolean thunderHighlight = true;   // thunder glow
    public boolean odgerWaypoint = true;      // odger wp
    public boolean wormholeLocator = true;    // wormhole loc
    public boolean sharkCounter = true;        // sharks
    public boolean totemTimer = true;          // totem time
    public boolean cocoonAlert = true;         // cocoon warn
    public boolean baitDisplay = true;         // bait display
    public boolean lavaFishingHelper = true;   // lava spots
    public boolean chumHider = true;           // chum hide
    public boolean seaCreatureHealthHud = true; // sc hp
    public boolean fishingRodTimerHud = true;   // rod colour
    public boolean baitWarningsHud = true;       // bait warn (cmp. Skyblocker)
    public boolean lavaFishingTimer = true;       // lava cast (cmp. Skyblocker)
    public boolean seaCreatureHealthOverlay = true; // sc hp bar (cmp. Skyblocker)
    public boolean fishingAchievementsHelper = true; // fish achs (cmp. Skyblocker)
    public boolean hotspotRadarGuesser = true;    // hotspot guess (cmp. Skyblocker)
}
