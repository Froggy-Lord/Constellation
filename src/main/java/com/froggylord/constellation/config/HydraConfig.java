package com.froggylord.constellation.config;

/** Config for the Hydra constellation (fishing). */
public class HydraConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean seaCreatureAlerts = true;
    public boolean rareSeaCreatureAlert = true;
    public boolean rareSeaCreaturePartyPing = false;
    public boolean hideOtherHooks = false;
    public boolean trophyFishTracker = true;
}
