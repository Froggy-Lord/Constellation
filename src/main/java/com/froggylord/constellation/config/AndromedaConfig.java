package com.froggylord.constellation.config;

/** Config for the Andromeda constellation (the Rift). */
public class AndromedaConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean timeHud = true;
    public boolean enigmaSoulTracker = true;
    public boolean riftLowTimeAlert = true;
}
