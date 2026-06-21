package com.froggylord.constellation.config;

/** Config for the Lyra constellation (economy + inventory). */
public class LyraConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean purseHud = true;
    public boolean coinSession = true;
    public boolean bitsHud = false;
}
