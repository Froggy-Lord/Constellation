package com.froggylord.constellation.config;

/** Config for the Draco constellation (Crimson Isle). */
public class DracoConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean activityHud = true;
    public boolean vanquisherAlert = true;
    public boolean vanquisherShare = false; // shout coords to party
    public boolean kuudraPhaseHud = true;
    public boolean ashfangFreezeTimer = true;
    public boolean dojoScoreHud = true;  // live Dojo minigame score
}
