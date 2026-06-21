package com.froggylord.constellation.config;

/** Config for the Cygnus constellation (events + Diana + calendar). */
public class CygnusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean calendarHud = true;
}
