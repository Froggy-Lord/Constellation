package com.froggylord.constellation.config;

/** Config for the Andromeda constellation (the Rift). */
public class AndromedaConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean timeHud = true;
    public boolean enigmaSoulTracker = true;
    public boolean riftLowTimeAlert = true;
    public boolean effigyTracker = true; // count effigies found
    public boolean dreadfarmHelper = true; // Dreadfarm area hints
    public boolean livingCaveHelper = true; // Living Cave area hints
    public boolean blobbercystGlow = true; // highlight Blobbercysts in world (cmp. Skyblocker)
    public boolean mountainTopHelper = true;  // Mountain Top area hints
    public boolean stillgoreHelper = true;    // Stillgore Chateau area hints
    public boolean colosseumHelper = true;     // Colosseum area hints
    public boolean danceRoomHelper = true;     // Dance Room area hints
    public boolean westVillageHelper = true;   // West Village area hints
    public boolean wyldWoodsHelper = true;     // Wyld Woods area hints
    public boolean deadgehogCounter = true;    // count Deadgehogs killed in Rift (cmp. Skyblocker)
    public boolean shyFarmHelper = true;        // Shy farm/crux counter in Dreadfarm (cmp. Skyblocker)
    public boolean moteProfitTracker = true;    // track motes gained/lost this session (cmp. Skyblocker)
}
