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
}
