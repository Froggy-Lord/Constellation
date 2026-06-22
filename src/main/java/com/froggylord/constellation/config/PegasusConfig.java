package com.froggylord.constellation.config;

public class PegasusConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean autoRejoin = true;
    public boolean trackParty = true;
    public boolean partyMembersHud = false;
    public boolean carryMode = false; 
    public boolean readyChecker = true;    
    public boolean partyTriggers = true;   
    public boolean discordRpc = false;     
    public boolean markedPlayers = true;   
    public boolean deathHighlightFrames = true; 
    public boolean customNameReplacer = false;  // replace player names with nick...
    public boolean partyTriggerSystem = true;   
    public boolean streamerModeParty = false;    
    public boolean readyCheckPing = true;        
    public boolean friendListHud = true;          
    public boolean partyMute = false;             // mute party chat temporarily (c...
    public boolean partyChatCommands = true;       
    public boolean dungeonReadyOverlay = true;     
    public boolean friendJoinLeaveAlert = true;    
    public boolean nicknameReplacer = true;        
    public boolean offlineMemberIndicator = true;  
}
