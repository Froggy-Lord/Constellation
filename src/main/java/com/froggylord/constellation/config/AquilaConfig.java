package com.froggylord.constellation.config;

/** Config for the Aquila constellation (mining). */
public class AquilaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean powderHud = true;
    public boolean commissionHud = true;
}
