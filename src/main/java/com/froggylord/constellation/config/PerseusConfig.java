package com.froggylord.constellation.config;

/** Config for the Perseus constellation (slayers + bestiary). */
public class PerseusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean bossTimer = true;
    public boolean xpBar = true;
    public boolean slayerTimer = true; // live kill timer + session best
    public boolean brokenHyperionAlert = true; // warn when wither blade runs out of charges (cmp. Skyblocker)
}
