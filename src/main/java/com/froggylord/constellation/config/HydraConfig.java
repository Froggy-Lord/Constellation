package com.froggylord.constellation.config;

/** Config for the Hydra constellation (fishing). */
public class HydraConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean seaCreatureAlerts = true;
    public boolean rareSeaCreatureAlert = true;
    public boolean rareSeaCreaturePartyPing = false;
    public boolean hideOtherHooks = false;
    public boolean trophyFishTracker = true;
    public boolean goldenFishTimer = true;  // timer since last golden fish (cmp. Skyblocker)
    public boolean barnTimer = true;         // barn fishing timer (cmp. Skyblocker)
    public boolean thunderHighlight = true;   // box the Thunder boss (cmp. Skyblocker)
    public boolean odgerWaypoint = true;      // waypoint to Odger (cmp. SkyHanni)
    public boolean wormholeLocator = true;    // wormhole chat hints (cmp. Skyblocker)
    public boolean sharkCounter = true;        // track shark kills (cmp. Skyblocker)
    public boolean totemTimer = true;          // show time since totem placed (cmp. Skyblocker)
    public boolean cocoonAlert = true;         // cocoon spawn warning (cmp. Skyblocker)
    public boolean baitDisplay = true;         // current bait + remaining uses (cmp. Skyblocker)
    public boolean lavaFishingHelper = true;   // highlight lava fishing spots (cmp. Skyblocker)
    public boolean chumHider = true;           // hide chum buckets in water (cmp. Skyblocker)
    public boolean seaCreatureHealthHud = true; // show last SC health / kill time (cmp. Skyblocker)
    public boolean fishingRodTimerHud = true;   // colour-change at 20s mark (cmp. Skyblocker)
}
