package com.froggylord.constellation.config;

/** Config for the Pegasus constellation (party + social). */
public class PegasusConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean autoRejoin = true;
    public boolean trackParty = true;
    public boolean partyMembersHud = false;
}
