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
    public boolean abiphoneHud = true;   // show who's calling (cmp. Skyblocker AbiphoneHud)
    public boolean crimsonFogBoost = true; // increase fog radius on Crimson Isle (cmp. Skyblocker)
    public boolean factionQuestHud = true; // show active faction quests (cmp. Skyblocker)
    public boolean dojoChallengeHelper = true; // show Dojo challenge type + tips (cmp. Skyblocker)
}
