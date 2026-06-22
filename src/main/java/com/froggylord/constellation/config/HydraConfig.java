package com.froggylord.constellation.config;

public class HydraConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean seaCreatureAlerts = true;
    public boolean rareSeaCreatureAlert = true;
    public boolean rareSeaCreaturePartyPing = false;
    public boolean hideOtherHooks = false;
    public boolean trophyFishTracker = true;
    public boolean goldenFishTimer = true;  // golden fish
    public boolean barnTimer = true;         // barn timer
    public boolean thunderHighlight = true;   
    public boolean odgerWaypoint = true;      
    public boolean wormholeLocator = true;    // wormhole loc
    public boolean sharkCounter = true;        
    public boolean totemTimer = true;          
    public boolean cocoonAlert = true;         
    public boolean baitDisplay = true;         
    public boolean lavaFishingHelper = true;   
    public boolean chumHider = true;           
    public boolean seaCreatureHealthHud = true; 
    public boolean fishingRodTimerHud = true;   // rod colour
    public boolean baitWarningsHud = true;       
    public boolean lavaFishingTimer = true;       
    public boolean seaCreatureHealthOverlay = true; 
    public boolean fishingAchievementsHelper = true; 
    public boolean hotspotRadarGuesser = true;
    public boolean chumBucketTimer = true;
    public boolean seaCreatureRarityDisplay = true;
    public boolean fishingProgressionHud = true;
    public boolean seaCreatureKillTimeHud = true;
}
