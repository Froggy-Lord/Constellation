package com.froggylord.constellation.config;

/** Config for the Perseus constellation (slayers + bestiary). */
public class PerseusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean bossTimer = true;
    public boolean xpBar = true;
}
