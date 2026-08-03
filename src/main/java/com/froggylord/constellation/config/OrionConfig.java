package com.froggylord.constellation.config;

import com.froggylord.constellation.hud.HudPosition;

public class OrionConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    
    public boolean scoreHud = true;
    // off by default — the consolidated score panel (scoreHud) already shows these; kept as
    // opt-in standalone widgets for players who prefer separate lines.
    public boolean secretsHud = false;
    public boolean cryptsHud = false;
    public boolean deathsHud = false;
    public boolean puzzlesDisplay = true;
    public boolean puzzlesCompact = true;
    public boolean timerHud = true;
    public boolean milestoneHud = true;
    public boolean roomNameHud = true;
    public boolean mimicIndicator = true;
    public boolean splitsHud = false;
    // per-floor personal-best splits, keyed by floor id (e.g. "F7"): {bloodMs, bossMs, clearMs}. 0 = unset.
    public java.util.Map<String, long[]> splitPBs = new java.util.HashMap<>();
    public boolean saveDungeonRunHistory = true;
    public int dungeonRunHistoryLimit = 500;
    public String dungeonRunExportFolder = "config/constellation-run-stats";
    public java.util.List<DungeonRunRecord> dungeonRunHistory = new java.util.ArrayList<>();

    public static class DungeonRunRecord {
        public long timestamp;
        public String floor = "";
        public int score;
        public String grade = "";
        public int secrets;
        public int deaths;
        public int crypts;
        public long totalMs;
        public long bloodMs;
        public long bossMs;
        public java.util.List<Long> terminalMs = new java.util.ArrayList<>();
    }

    
    public boolean scorePings = true;
    public boolean scorePingTitle = true;
    public boolean scorePingSound = true;

    
    public boolean dungeonMap = true;
    public int mapScale = 2; 
    public int mapX = 1;     
    public int mapY = 2;

    
    public boolean secretWaypoints = true;
    public boolean secretCompassHelper = true;
    public boolean secretCompassTracer = true;
    public boolean secretCompassBeam = true;
    public boolean secretCompassBox = true;
    public boolean secretCompassThroughWalls = true;
    public boolean secretCompassDuplicateGuard = true;
    public boolean secretCompassHud = true;
    public int secretCompassColour = 0xFFFF55FF;
    public boolean secretBeams = true;
    public boolean progressiveReveal = true;
    public boolean oneSecretAtATime = false;
    public boolean routes = true;
    public boolean pearlRoutes = true;
    public boolean routeLines = true;
    public boolean routeMarkers = true;
    public boolean routeWholeRoute = true;
    public int routeVisibleSteps = 1;
    public boolean routeThroughWalls = true;
    public boolean routeAutoAdvance = true;
    public boolean routeRecordingHud = true;
    public boolean routeDistinguishFuture = true;
    public boolean routeFilledMarkers = false;
    public boolean routeLabels = true;
    public boolean routeRenderSecrets = true;
    public boolean routeRenderEtherwarps = true;
    public boolean routeRenderMines = true;
    public boolean routeRenderInteracts = true;
    public boolean routeRenderSuperboom = true;
    public boolean routeRenderPearls = true;
    public boolean routePlayerToSecret = false;
    public boolean routePlayerToEtherwarp = false;
    public int routeLineColour = 0xFFFF0000;
    public int routePearlLineColour = 0xFF00FFFF;
    public boolean customWaypoints = false;
    public boolean echoOnCollect = true;
    public boolean perRoomCount = true;
    public boolean secretsDoneAlert = true;
    public boolean quickCloseDungeonChests = true;
    public boolean quickCloseAnyKey = true;
    public boolean quickCloseMovementKeys = true;
    public boolean quickCloseCrouchKey = true;
    public boolean quickCloseRewardChests = false;
    public boolean salvageHelper = true;
    public int salvageMaxValue = 100_000;
    public boolean salvageExcludeProtected = true;
    public boolean salvageExcludeModified = true;
    public boolean salvageMarkUnknown = true;
    public int salvageSafeColour = 0xFFFFAA00;
    public int salvageUnknownColour = 0xFFFF5555;
    public boolean sellableDungeonLoot = true;
    public boolean sellableIncludeHotbar = false;
    public int sellableDungeonLootColour = 0xFFFFFF00;

    
    public boolean partyFinderGui = true;
    public boolean partyFinderStats = true;
    public boolean partyGuard = false;
    public boolean partyGuardDryRun = true;
    public boolean partyGuardSendReason = true;
    public boolean partyGuardPrivateReason = false;
    public boolean partyGuardKickMissingPb = false;
    public int partyGuardMinCata = 0;
    public int partyGuardMinSecrets = 0;
    public double partyGuardMinAverageSecrets = 0;
    public int partyGuardMinMagicalPower = 0;
    public int partyGuardMaxPbSeconds = 0;
    public int partyGuardKickDelayTicks = 10;
    public String partyGuardFloor = "AUTO";
    public String partyGuardKickMessage = "{player}, kicked: {reasons}";
    public java.util.Set<String> partyGuardWhitelist = new java.util.HashSet<>();
    public java.util.Set<String> partyGuardBlacklist = new java.util.HashSet<>();
    public boolean partyMessages = true;
    public java.util.Map<String, String> partyMessageTemplates = new java.util.HashMap<>();
    public java.util.Map<String, Boolean> partyMessageEnabled = new java.util.HashMap<>();

    
    public boolean autoRequeue = false;
    public int requeueDelaySec = 3;
    public boolean requeueCancelOnPartyChange = true;
    public boolean requeueDowntime = true;
    public boolean requeueFeedback = true;
    public boolean dungeonQueueCooldown = true;
    public boolean dungeonQueueBlockCommands = false;
    public boolean dungeonQueueTransferRecovery = true;

    
    public boolean starredMobs = true;
    public boolean secretBats = true;
    public boolean teammateBoxes = false;
    public boolean deathmiteHighlight = true;
    public boolean deathmiteTracer = false;
    public boolean felHighlight = true;
    public boolean felTracer = false;
    public boolean felHighlightActive = false;
    public boolean hideSkeletonSkulls = true;
    public boolean highlightMovingSkeletonSkulls = true;
    public boolean hideSoulweaverSkulls = true;

    
    public boolean abilityTracker = true;
    public boolean abilityReadyDing = true;
    public boolean spiritMaskTracker = true;
    public boolean spiritMaskOnlyDungeons = false;
    public boolean spiritMaskUsedAlert = true;
    public boolean spiritMaskUsedTitle = true;
    public boolean spiritMaskUsedChat = false;
    public boolean spiritMaskUsedSound = true;
    public boolean spiritMaskReadyAlert = false;
    public boolean spiritMaskReadyTitle = true;
    public boolean spiritMaskReadyChat = true;
    public boolean spiritMaskReadySound = true;
    public boolean spiritMaskHud = true;
    public boolean spiritMaskHudShowEquipped = true;
    public boolean spiritMaskHudShowImmunity = true;
    public boolean spiritMaskHudShowReady = false;
    public boolean spiritMaskItemCooldown = true;
    public boolean spiritMaskItemText = true;
    public boolean spiritMaskItemShade = true;
    public int spiritMaskCooldownSeconds = 30;
    public int spiritMaskImmunityMillis = 3000;
    public int spiritMaskTitleTicks = 40;
    public int spiritMaskUsedColor = 0xFFFF55FF;
    public int spiritMaskReadyColor = 0xFF55FF55;
    public int spiritMaskCooldownColor = 0xAA262626;
    public String spiritMaskUsedTemplate = "Spirit Mask used";
    public String spiritMaskReadyTemplate = "Spirit Mask is ready";
    public boolean lowHealthAlert = true;
    // health-fraction threshold for the low-hp title/sound, as a percent of max health
    public int lowHealthPercent = 25;
    public boolean spiritLeapHelper = true;
    public boolean spiritLeapCustomGui = true;
    public int spiritLeapSorting = 0;
    public boolean spiritLeapStaticSlots = true;
    public int spiritLeapBackground = 0xE6191919;
    public boolean spiritLeapClickOnPress = true;
    public int spiritLeapScalePercent = 100;
    public boolean spiritLeapShowClass = true;
    public boolean spiritLeapShowDead = true;
    public java.util.List<String> spiritLeapCustomOrder = new java.util.ArrayList<>();
    public boolean leapCounter = true;
    public boolean leapCounterAlert = true;
    public boolean leapCounterSound = true;
    // opt-in: class leap keys (1-5 = Archer/Berserk/Healer/Mage/Tank) act while the Spirit Leap menu is open.
    // off by default so nothing ever leaps without the player deliberately enabling + pressing a key.
    public boolean spiritLeapKeybinds = false;
    public boolean dungeonCopilot = true;
    public boolean doorTracker = true;
    // advisory-only etherwarp destination box (render only, never auto-warps); off by default
    public boolean etherwarpHelper = false;
    public boolean mimicPartyPing = true;
    public boolean princePartyPing = true;
    public boolean streamerMode = false;
    public boolean rareDropAlerts = true;
    public boolean architectNotifier = true;
    public boolean smartRefill = true;
    public boolean smartRefillOneAtATime = true;
    public int smartRefillCooldownTicks = 20;
    public java.util.Map<String, Integer> smartRefillTargets = new java.util.LinkedHashMap<>(java.util.Map.of(
        "ENDER_PEARL", 16, "SPIRIT_LEAP", 16, "SUPERBOOM_TNT", 64,
        "INFLATABLE_JERRY", 64, "ARCHITECT_FIRST_DRAFT", 1,
        "TWILIGHT_ARROW_POISON", 16, "TOXIC_ARROW_POISON", 32));
    public java.util.Set<String> smartRefillEnabled = new java.util.LinkedHashSet<>(java.util.List.of(
        "ENDER_PEARL", "SPIRIT_LEAP", "SUPERBOOM_TNT"));
    public boolean springBootsHelper = true;
    public boolean springBootsHud = true;
    public boolean springBootsBox = true;
    public boolean springBootsLine = true;
    public boolean springBootsThroughWalls = true;
    public int springBootsColour = 0xFF55FFFF;

    
    public boolean terminalSolvers = true;
    public boolean terminalNumbers = true;
    public boolean blockWrongTerminalClicks = false;
    public boolean terminalMiddleClick = true;
    public boolean terminalDropKeyClick = true;
    public boolean terminalDisableTooltips = true;
    public boolean terminalHideLabels = true;
    public boolean terminalSlotBackground = true;
    public int terminalSlotBackgroundColour = 0xFF191919;
    public boolean terminalHideDone = true;
    public boolean terminalHideItems = true;
    public boolean terminalRubixBlockBadDirection = true;
    public boolean terminalClickSounds = true;
    public float terminalClickSoundVolume = 0.3f;
    public boolean terminalBreakdown = true;
    public boolean terminalDisplay = true;
    public boolean terminalDisplaySimple = true;
    public boolean terminalDisplayShowSection = true;
    public boolean goldorWaypoints = true;
    public boolean goldorWaypointFixedPositions = true;
    public boolean goldorWaypointTerminals = true;
    public boolean goldorWaypointDevices = true;
    public boolean goldorWaypointLevers = true;
    public boolean goldorWaypointHideCompleted = true;
    public boolean goldorWaypointClassFilter = true;
    public boolean goldorWaypointShowClass = true;
    public boolean goldorWaypointLabels = true;
    public boolean goldorWaypointBeam = false;
    public boolean goldorWaypointFilled = true;
    public boolean goldorWaypointOutline = true;
    public boolean goldorWaypointThroughWalls = true;
    public int goldorTerminalColour = 0xC800FFFF;
    public int goldorDeviceColour = 0xC85555FF;
    public int goldorLeverColour = 0xC8FFFF00;
    public java.util.Map<String, String> goldorWaypointAssignments = new java.util.LinkedHashMap<>();
    public boolean goldorInactiveTerminals = true;
    public boolean terminalHideCompletion = true;
    public boolean terminalCompletionOnlyOwn = true;
    public boolean terminalCompletionFilterTitles = true;
    public boolean terminalCompletionFilterSubtitles = true;

    
    public boolean blazeSolver = true;

    
    public boolean blessingDisplay = true;

    
    public boolean dropEsp = true;

    // puzzle solvers
    public boolean simonSaysSolver = true;
    public boolean threeWeirdosSolver = true;
    public boolean triviaSolver = true;
    public boolean creeperBeamsSolver = true;


    public boolean lividFinder = true;
    public boolean lividInvulnerableTimer = true;
    public boolean bloodTimer = true;
    public boolean bloodCampHelper = true;
    public boolean watcherBossBar = true;
    public boolean watcherBossBarShowProgress = true;
    public boolean watcherBossBarHideNotBlood = true;
    public boolean watcherBossBarShowPercent = false;
    public boolean watcherBossBarShowRemaining = false;
    public int watcherBossBarProgressColour = 0xFFAAFF;
    public int watcherBossBarSeparatorColour = 0x555555;

    
    public boolean ticTacToeSolver = true;

    
    public boolean fireFreezeTimer = true;

    
    public boolean guardianHealth = true;
    public boolean healerPlatformHighlight = true;
    public boolean shadowAssassinAlert = true;

    
    public boolean minibossHighlights = true;
    public boolean rareRoomAlerts = true;
    public boolean mageBeamCleaner = true; 
    public int mageBeamDurationTicks = 40;
    public int mageBeamColour = 0xFFAA0000;
    public boolean mageBeamDepthCheck = true;
    public boolean mageBeamHideParticles = true;
    public int mageBeamMinPoints = 3;
    public boolean spiritBowTimer = true;  
    public boolean spiritBearTimer = true;
    public boolean spiritBowHighlight = true;
    public boolean melodyTerminalHelper = true; 
    public boolean saVanishTimer = true;    
    public boolean dungeonPotionsHud = true; 
    public boolean dungeonBreakerDisplay = true;
    public boolean chestProfitCalc = true;    
    public boolean chestProfitUseEssence = true;
    public boolean chestProfitCompact = true;
    public boolean chestProfitSubtractKey = true;
    public boolean chestProfitShowUnknown = true;
    public boolean chestProfitHud = true;
    public boolean m7DragonMarkers = true;    
    public boolean m7DragonStackAimer = true;
    public boolean m7DragonStackPing = true;
    public boolean m7DragonStackHud = true;
    public boolean m7DragonHitCounter = true;
    public boolean m7DragonHitHud = true;
    public boolean m7DragonHitReport = true;
    public boolean m7DragonHitPartyMessage = false;
    public boolean m7RelicHighlight = true;
    public boolean m7RelicTimer = true;
    public boolean witherHighlight = true;
    public boolean witherHighlightOutline = true;
    public boolean witherHighlightFill = false;
    public boolean witherHighlightThroughWalls = true;
    public boolean witherHighlightLabel = false;
    public boolean witherHighlightBeam = false;
    public boolean witherHighlightHideInvisible = true;
    public boolean witherHighlightExcludeArmorSummon = true;
    public boolean witherHighlightMaxor = true;
    public boolean witherHighlightStorm = true;
    public boolean witherHighlightGoldor = true;
    public boolean witherHighlightNecron = true;
    public boolean witherHighlightWitherKing = false;
    public int witherHighlightWireColour = 0xFF12DE34;
    public int witherHighlightFillColour = 0xA012DE34;
    public int witherHighlightLineWidth = 3;
    public int witherHighlightRange = 256;
    public boolean terracottaTimer = true;
    public boolean terracottaRespawnLabels = true;
    public boolean terracottaPhaseHud = true;
    public boolean terracottaThroughWalls = true;
    public boolean terracottaReadySound = false;
    public int terracottaTimerDecimals = 1;
    public int terracottaF6RespawnTicks = 300;
    public int terracottaM6RespawnTicks = 240;
    public int terracottaFarColour = 0xFFFF5555;
    public int terracottaSoonColour = 0xFFFFFF55;
    public int terracottaReadyColour = 0xFF55FF55;
    public boolean waterboardSolver = true;    // highlight water puzzle gates (...
    public boolean iceFillSolver = true;       
    public boolean teleportMazeSolver = true;  
    public boolean silverfishSolver = true;    // highlight silverfish bfs path ...
    public boolean lightsOnSolver = true;     
    public boolean arrowAlignSolver = true;    
    public boolean targetPracticeSolver = true;
    public boolean terminalSimulator = true;
    public boolean secretRoutesOnlineDb = true;
}
