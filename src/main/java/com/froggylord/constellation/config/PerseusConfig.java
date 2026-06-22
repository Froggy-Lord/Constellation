package com.froggylord.constellation.config;

/** Config for the Perseus constellation (slayers + bestiary). */
public class PerseusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean bossTimer = true;
    public boolean xpBar = true;
    public boolean slayerTimer = true; // live kill timer + session best
    public boolean brokenHyperionAlert = true; // warn when wither blade runs out of charges (cmp. Skyblocker)
    public boolean skillLevelUpAlert = true;   // title ping on skill level-up (cmp. Skyblocker)
    public boolean bestiaryTracker = true;     // bestiary kill counter per family (cmp. Skyblocker)
    public boolean rngMeterDetail = true;       // show RNG meter progress + drops (cmp. Skyblocker)
    public boolean rareDropEffect = true;        // special effect when rare drop appears (cmp. Skyblocker)
    public boolean vampireHelper = true;          // Vampire Slayer boss hints (cmp. Skyblocker)
    public boolean endermanHelper = true;          // Enderman Slayer beacon/laser hints (cmp. Skyblocker)
    public boolean blazeHelper = true;             // Blaze Slayer attunement hints (cmp. Skyblocker)
    public boolean spawnAlertTitle = true;          // title ping on boss spawn (cmp. SBA)
    public boolean minibossFlash = true;            // flash screen on miniboss spawn (cmp. SBA)
    public boolean bestiaryMilestoneAlert = true;   // ping on bestiary milestone reached (cmp. Skyblocker)
    public boolean slayerBossSpawnCustomSound = true; // custom dragon growl on boss spawn (cmp. Skyblocker)
    public boolean sosFlareDisplay = true;       // show SOS flare from other players (cmp. Skyblocker)
    public boolean slayerProfitTracker = true;   // track coins gained per slayer session (cmp. Skyblocker)
    public boolean tarantulaInvincMark = true;   // mark invincible tarantulas in spider slayer (cmp. Skyblocker)
    public boolean hideIrrelevantMobs = true;      // hide non-slayer mobs during quests (cmp. Skyblocker)
    public boolean damageIndicatorHud = true;       // show DPS/hits/time on boss (cmp. Skyblocker)
}
