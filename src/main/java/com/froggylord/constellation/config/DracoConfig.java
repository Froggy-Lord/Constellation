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
    public boolean trophyFishingHud = true;    // crimson isle trophy fishing stats (cmp. Skyblocker)
    public boolean freshToolsTimer = true;     // countdown for fresh tools buff (cmp. Skyblocker)
    public boolean dangerWarningHud = true;    // warn when danger imminent in Kuudra (cmp. Skyblocker)
    public boolean supplyObjectiveHud = true;  // show Kuudra supply objectives (cmp. Skyblocker)
}
