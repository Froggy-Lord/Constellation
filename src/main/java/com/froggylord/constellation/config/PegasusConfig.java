package com.froggylord.constellation.config;

public class PegasusConfig extends BaseConfigGroup {
    { enabled = true; }
    @Override public int currentVersion() { return 0; }
    public boolean autoRejoin = true;
    public boolean trackParty = true;
    public boolean partyMembersHud = false;
    public boolean carryMode = true;
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
    public boolean partyTransferHelper = true;
    public boolean partyKickConfirm = true;
    public boolean partyInviteCooldown = true;
    public boolean partyChatFilter = true;
    public boolean carryTracker = true;
    public boolean carryHud = true;
    public boolean carryPartyProgress = true;
    public boolean carryAutoDungeon = true;
    public boolean carryAutoKuudra = true;
    public boolean carryAutoSlayer = true;
    public boolean carryPaymentDetection = true;
    public boolean carryPaymentChat = true;
    public boolean carryShowStartMessage = true;
    public boolean carryHudOnlyRelevantArea = true;
    public boolean carryHudShowPayment = true;
    public boolean carryHudShowRate = true;
    public boolean carryHighlightPlayer = true;
    public boolean carryHighlightThroughWalls = true;
    public boolean carryHighlightLabel = false;
    public float carryHighlightLineWidth = 2.0f;
    public int carryHighlightColour = 0x9600FFFF;
    public boolean carrySlayerSpawnMessage = true;
    public String carrySlayerSpawnTemplate = "Boss spawned for {player} [{target}]";
    public boolean carrySlayerHighlightPlayer = true;
    public boolean carrySlayerPlayerThroughWalls = true;
    public boolean carrySlayerPlayerLabel = false;
    public int carrySlayerPlayerColour = 0x9600FFFF;
    public float carrySlayerPlayerLineWidth = 2.0f;
    public boolean carrySlayerHighlightBoss = true;
    public boolean carrySlayerHighlightThroughWalls = true;
    public int carrySlayerBossColour = 0x96FF0000;
    public float carrySlayerBossLineWidth = 2.0f;
    public int carryPartyProgressCooldownMs = 1_000;
    public String carryStartMessage = "Kuudra started for {player} [{target}]";
    public String carryPartyProgressMessage = "{player}: {completed}/{total} {target}";
    public boolean carryWebhook = false;
    public boolean carryWebhookEach = true;
    public boolean carryWebhookCompletion = true;
    public boolean carryWebhookErrors = true;
    public String carryWebhookUrl = "";
    public int carryHistoryLimit = 500;
    public java.util.Map<String, CarryData> carries = new java.util.LinkedHashMap<>();
    public java.util.List<CarryHistory> carryHistory = new java.util.ArrayList<>();

    public static class CarryData {
        public String player = "";
        public String type = "DUNGEON";
        public String target = "F7";
        public int total;
        public int completed;
        public long pricePerRun;
        public long paid;
        public int paidRuns;
        public long lastPaymentAt;
        public long createdAt;
        public long firstCompletion;
        public long lastCompletion;
    }

    public static class CarryHistory {
        public String player = "";
        public String type = "";
        public String target = "";
        public int completed;
        public int total;
        public long pricePerRun;
        public long paid;
        public long durationMs;
        public long timestamp;
    }
}
