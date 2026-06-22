package com.froggylord.constellation.config;

/** Config for the Cygnus constellation (events + Diana + calendar). */
public class CygnusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean calendarHud = true;

    // Diana / mythological event
    public boolean dianaInquisitorAlert = true;
    public boolean dianaInquisitorShare = false; // shout coords to party chat
    public boolean dianaDropTracker = true;
}
