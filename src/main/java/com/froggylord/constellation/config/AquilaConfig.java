package com.froggylord.constellation.config;

/** Config for the Aquila constellation (mining). */
public class AquilaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean powderHud = true;
    public boolean commissionHud = true;
    public boolean coldWarning = true;   // title pings at cold thresholds (no vignette)
    public boolean coldHud = true;
    public boolean mineshaftAlert = true;
    public boolean hotmHud = true;
}
