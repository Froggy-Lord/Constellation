package com.froggylord.constellation.config;

/** Config for the Cygnus constellation (events + Diana + calendar). */
public class CygnusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean calendarHud = true;

    // Diana / mythological event
    public boolean dianaInquisitorAlert = true;
    public boolean dianaInquisitorShare = false; // shout coords to party chat
    public boolean dianaDropTracker = true;
    public boolean dianaBurrowWaypoints = true; // triangulate burrow from spade directions
    public boolean carnivalHelper = true; // Catch a Fish / Zombie Shootout hints
    public boolean newYearCakeTracker = true; // show collected cake years
    public boolean jerryTimer = true; // show time until next Jerry event
    public boolean seasonDisplay = true; // show current SkyBlock season + upcoming
}
