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
    public boolean spookyEventTracker = true; // Spooky Festival candy counter
    public boolean raffleHelper = true;        // raffle event task highlighter (cmp. Skyblocker)
    public boolean mayorPerksDisplay = true;    // show active mayor + perks (cmp. Skyblocker)
    public boolean dianaBurrowGuesser = true;   // estimate burrow location from spade (cmp. Skyblocker)
    public boolean chimeraAlert = true;          // alert when Chimera book drops (cmp. Skyblocker)
    public boolean daedalusAlert = true;         // alert when Daedalus Stick drops (cmp. Skyblocker)
    public boolean mayorElectionHud = true;      // upcoming election info (cmp. Skyblocker)
    public boolean eventNotificationHud = true;   // upcoming event countdown (cmp. Skyblocker)
    public boolean carnivalScoreTracker = true;   // track carnival minigame scores (cmp. Skyblocker)
    public boolean lobbySeasonalDecorations = true; // lobby event waypoints (cmp. Skyblocker)
}
