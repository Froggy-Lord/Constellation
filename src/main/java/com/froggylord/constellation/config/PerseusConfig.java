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
}
