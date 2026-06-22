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
}
