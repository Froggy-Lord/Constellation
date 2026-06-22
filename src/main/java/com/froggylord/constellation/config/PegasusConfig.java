package com.froggylord.constellation.config;

/** Config for the Pegasus constellation (party + social). */
public class PegasusConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean autoRejoin = true;
    public boolean trackParty = true;
    public boolean partyMembersHud = false;
    public boolean carryMode = false; // track !paid commands + carry ledger
    public boolean readyChecker = true;    // ready check overlay (cmp. Skyblocker)
    public boolean partyTriggers = true;   // !warp !join !dt commands (cmp. Skyblocker)
    public boolean discordRpc = false;     // show dungeon status on Discord (cmp. Skyblocker)
    public boolean markedPlayers = true;   // notify when marked friends join (cmp. Skyblocker)
    public boolean deathHighlightFrames = true; // red frame on dead party members (cmp. Skyblocker)
    public boolean customNameReplacer = false;  // replace player names with nicknames (cmp. Skyblocker)
    public boolean partyTriggerSystem = true;   // !warp !join !ptme bot commands (cmp. Skyblocker)
    public boolean streamerModeParty = false;    // hide party members' names in screenshots
    public boolean readyCheckPing = true;        // sound when all party members ready (cmp. Skyblocker)
    public boolean friendListHud = true;          // show online friends count (cmp. Skyblocker)
    public boolean partyMute = false;             // mute party chat temporarily (cmp. Skyblocker)
}
