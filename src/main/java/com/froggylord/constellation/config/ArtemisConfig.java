package com.froggylord.constellation.config;

import java.util.HashMap;
import java.util.Map;

public class ArtemisConfig extends BaseConfigGroup {
    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/hunting/HuntingProfitTrackerConfig.kt
    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/HuntingProfitTracker.kt
    public boolean huntingProfitTracker = true;
    public boolean huntingProfitHud = true;
    public boolean huntingProfitShowWhenPickup = true;
    public boolean huntingProfitAlwaysShow = false;
    public boolean huntingProfitShowWithTool = true;
    public boolean huntingProfitPersistent = false;
    public boolean huntingProfitShowRecent = true;
    public boolean huntingProfitShowTable = true;
    public boolean huntingProfitShowProfitPerHour = true;
    public boolean huntingProfitShowMobs = true;
    public boolean huntingProfitShowShards = true;
    public boolean huntingProfitShowUptime = true;
    public boolean huntingProfitChatWarning = true;
    public boolean huntingProfitTitleWarning = true;
    public boolean huntingProfitSoundWarning = true;
    public boolean huntingProfitIncludeLootshare = true;
    public boolean huntingProfitIncludeCharm = true;
    public int huntingProfitRecentSeconds = 10;
    public int huntingProfitToolGraceSeconds = 10;
    public int huntingProfitAfkSeconds = 60;
    public int huntingProfitRows = 10;
    public int huntingProfitMinimumChatMillions = 5;
    public int huntingProfitMinimumTitleMillions = 5;
    public int huntingProfitNameColor = 0xFFFFFF55;
    public int huntingProfitValueColor = 0xFF55FF55;
    public int huntingProfitRecentColor = 0xFFFF55FF;
    public String huntingProfitPriceSource = "PURCHASE";
    public String huntingProfitSorting = "VALUE_DESC";
    public Map<String,Long> huntingProfitAmounts = new HashMap<>();
    public Map<String,String> huntingProfitNames = new HashMap<>();
    public Map<String,Long> huntingProfitLastGains = new HashMap<>();
    public Map<String,Long> huntingProfitMobs = new HashMap<>();
    public Map<String,Long> huntingProfitShards = new HashMap<>();
    public Map<String,Long> huntingProfitUptime = new HashMap<>();

    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/{HideonleafHighlighter,InvisibugHighlighter,BirriesHighlighter}.kt
    // ported from Skyblocker (LGPL-3.0-only): skyblock/entity/glow/adder/GalateaGlowAdder.java
    public boolean huntingMobHighlights = true;
    public boolean huntingHighlightHideonleaf = true;
    public boolean huntingHighlightInvisibug = true;
    public boolean huntingHighlightBirries = false;
    public boolean huntingHighlightShellwise = true;
    public boolean huntingHighlightCoralot = true;
    public boolean huntingMobBoxes = true;
    public boolean huntingMobLabels = true;
    public boolean huntingMobBeams = false;
    public boolean huntingMobLines = false;
    public boolean huntingMobDistances = true;
    public boolean huntingMobThroughWalls = true;
    public boolean huntingMobAlerts = false;
    public boolean huntingMobAlertChat = true;
    public boolean huntingMobAlertTitle = false;
    public boolean huntingMobAlertSound = true;
    public int huntingHideonleafRange = 20;
    public int huntingInvisibugRange = 32;
    public int huntingBirriesRange = 10;
    public int huntingShellwiseRange = 32;
    public int huntingCoralotRange = 32;
    public int huntingMobScanRange = 64;
    public int huntingMobBeamHeight = 8;
    public int huntingMobAlertCooldownSeconds = 5;
    public int huntingHideonleafColor = 0xFFFF00FF;
    public int huntingInvisibugColor = 0xFF00FFFF;
    public int huntingBirriesColor = 0xFF00FF00;
    public int huntingShellwiseColor = 0xFFFF6800;
    public int huntingCoralotColor = 0xFF0000FF;

    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/LassoDisplay.kt
    // ported from Skyblocker (LGPL-3.0-only): skyblock/hunting/LassoHud.java
    public boolean lassoDisplay = true;
    public boolean lassoGalateaOnly = true;
    public boolean lassoShowProgress = true;
    public boolean lassoShowPercent = true;
    public boolean lassoShowTarget = true;
    public boolean lassoShowTool = false;
    public boolean lassoShowDistance = false;
    public boolean lassoCompact = false;
    public boolean lassoReadyAlert = true;
    public boolean lassoReadyChat = false;
    public boolean lassoReadyTitle = false;
    public boolean lassoReadySound = true;
    public boolean lassoReadyRepeat = false;
    public int lassoTargetSearchRange = 4;
    public int lassoReadyPercent = 0;
    public int lassoReadyRepeatSeconds = 2;
    public int lassoReadyColor = 0xFF55FF55;
    public int lassoProgressColor = 0xFFFFFF55;
    public int lassoTargetColor = 0xFF55FFFF;

    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/FusionDisplay.kt
    // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/FusionData.kt
    public boolean fusionDisplay = true;
    public boolean fusionShowInputs = true;
    public boolean fusionShowOutput = true;
    public boolean fusionShowOwned = true;
    public boolean fusionShowRequired = true;
    public boolean fusionShowInputCost = true;
    public boolean fusionShowOutputValue = true;
    public boolean fusionShowNetValue = true;
    public boolean fusionShowPureReptiles = true;
    public boolean fusionShowLastResult = true;
    public boolean fusionWarnMissing = true;
    public boolean fusionReadyAlert = false;
    public boolean fusionReadyChat = true;
    public boolean fusionReadyTitle = false;
    public boolean fusionReadySound = true;
    public boolean fusionPersistentReptiles = false;
    public int fusionReadyColor = 0xFF55FF55;
    public int fusionMissingColor = 0xFFFF5555;
    public int fusionInputColor = 0xFFFFFF55;
    public int fusionOutputColor = 0xFF55FFFF;
    public String fusionInputPriceSource = "PURCHASE";
    public String fusionOutputPriceSource = "SELL";
    public Map<String,Long> fusionPureReptiles = new HashMap<>();

    // ported from NoFrills (GPL-3.0-only): features/hunting/ShardTracker.java
    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/ShardTrackerDisplay.kt
    public boolean shardTracker = true;
    public boolean shardTrackerHud = true;
    public boolean shardTrackerHideEmpty = false;
    public boolean shardTrackerSyncBox = true;
    public boolean shardTrackerCompletionChat = true;
    public boolean shardTrackerCompletionTitle = false;
    public boolean shardTrackerCompletionSound = true;
    public boolean shardTrackerFilterFusionOutside = false;
    public boolean shardTrackerFilterDirectInside = false;
    public boolean shardTrackerShowSource = true;
    public boolean shardTrackerShowRemaining = true;
    public boolean shardTrackerShowValue = false;
    public boolean shardTrackerShowTotal = true;
    public boolean shardTrackerRemoveCompleted = false;
    public int shardTrackerRows = 20;
    public int shardTrackerIncompleteColor = 0xFFFFFF55;
    public int shardTrackerCompleteColor = 0xFF55FF55;
    public int shardTrackerMissingColor = 0xFFFF5555;
    public String shardTrackerSort = "IMPORT";
    public String shardTrackerPriceSource = "PURCHASE";
    public Map<String,Long> shardTrackerNeeded = new HashMap<>();
    public Map<String,Long> shardTrackerObtained = new HashMap<>();
    public Map<String,String> shardTrackerNames = new HashMap<>();
    public Map<String,String> shardTrackerSources = new HashMap<>();
    public Map<String,Long> shardTrackerOrder = new HashMap<>();

    // ported from NoFrills (GPL-3.0-only): features/hunting/FusionKeybinds.java
    // ported from SkyHanni (LGPL-3.0-or-later): features/hunting/FusionKeybinds.kt
    public boolean fusionKeybinds = true;
    public boolean fusionKeybindRepeat = true;
    public boolean fusionKeybindConfirm = true;
    public boolean fusionKeybindCancel = true;
    public boolean fusionKeybindConsume = true;
    public boolean fusionKeybindFeedback = true;
    public boolean fusionKeybindSound = true;
    public int fusionKeybindCooldownMillis = 200;

    // ported from NoFrills (GPL-3.0-only): features/hunting/HuntaxeLock.java
    public boolean huntaxeLock = true;
    public boolean huntaxeLockAir = true;
    public boolean huntaxeLockBlocks = true;
    public boolean huntaxeLockSingleUse = false;
    public boolean huntaxeLockSneakBypass = false;
    public boolean huntaxeLockActionbar = true;
    public boolean huntaxeLockChat = false;
    public boolean huntaxeLockSound = false;
    public int huntaxeLockTicks = 10;

    // ported from Skyblocker (LGPL-3.0-only): skyblock/hunting/SilencePhantoms.java
    // ported from SkyHanni (LGPL-3.0-or-later): features/foraging/{MutePhantom,MuteFusionMachine}.kt
    public boolean galateaSoundControl = true;
    public boolean galateaSoundsGalateaOnly = true;
    public boolean galateaMutePhantoms = true;
    public boolean galateaMutePhantomAmbient = true;
    public boolean galateaMutePhantomBite = true;
    public boolean galateaMutePhantomDeath = true;
    public boolean galateaMutePhantomFlap = true;
    public boolean galateaMutePhantomHurt = true;
    public boolean galateaMutePhantomSwoop = true;
    public boolean galateaMuteFusionMachine = true;
    public boolean galateaMuteFusionAnyVolume = false;
    public int galateaFusionVolumeHundredths = 2000;
    public int galateaFusionVolumeToleranceHundredths = 0;

    // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/HuntingBoxValue.kt
    public boolean huntingBoxValue = true;
    public boolean huntingBoxValueHud = true;
    public boolean huntingBoxValueShowRows = true;
    public boolean huntingBoxValueShowAmount = true;
    public boolean huntingBoxValueShowUnit = false;
    public boolean huntingBoxValueShowSell = true;
    public boolean huntingBoxValueShowBuy = false;
    public boolean huntingBoxValueShowTotalShards = true;
    public boolean huntingBoxValueShowTotalSell = true;
    public boolean huntingBoxValueShowTotalBuy = true;
    public boolean huntingBoxValueHideZero = true;
    public boolean huntingBoxValueTooltips = true;
    public boolean huntingBoxValueHighlights = true;
    public boolean huntingBoxValueHighlightThroughMissingPrice = false;
    public int huntingBoxValueRows = 15;
    public int huntingBoxValueHighlightMillions = 5;
    public int huntingBoxValueNormalColor = 0x6655AAFF;
    public int huntingBoxValueHighColor = 0x99FFAA00;
    public int huntingBoxValueMissingColor = 0x66FF5555;
    public String huntingBoxValueSort = "SELL_DESC";

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/inventory/AttributeShardsConfig.kt
    // ported from SkyHanni (LGPL-3.0-or-later): features/inventory/attribute/{AttributeShardOverlay,AttributeShardsData,AttributesShardsInventory}.kt
    public boolean attributeOverlay = true;
    public boolean attributeOverlayHud = true;
    public boolean attributeOverlayHideMaxed = true;
    public boolean attributeOverlayOnlyNotUnlocked = false;
    public boolean attributeOverlayIncludeHuntingBox = true;
    public boolean attributeOverlayOnlyCurrentInventory = false;
    public boolean attributeOverlayTierAsStackSize = false;
    public boolean attributeOverlayHighlightDisabled = true;
    public boolean attributeOverlayTooltips = true;
    public boolean attributeOverlayShowTier = true;
    public boolean attributeOverlayShowNeeded = true;
    public boolean attributeOverlayShowPrice = true;
    public boolean attributeOverlayShowBox = false;
    public boolean attributeOverlayShowSummary = true;
    public boolean attributeOverlayHideUnknownPrice = false;
    public int attributeOverlayRows = 20;
    public int attributeOverlayDisabledColor = 0x66FF5555;
    public int attributeOverlayUnknownColor = 0xFFFF5555;
    public int attributeOverlayNormalColor = 0xFFFFFF55;
    public int attributeOverlayCompleteColor = 0xFF55FF55;
    public String attributeOverlaySort = "PRICE_TO_MAXED";
    public String attributeOverlayPriceSource = "SELL";
    public Map<String,Integer> attributeOverlaySyphoned = new HashMap<>();
    public Map<String,Integer> attributeOverlayTier = new HashMap<>();
    public Map<String,Integer> attributeOverlayToNext = new HashMap<>();
    public Map<String,Integer> attributeOverlayBox = new HashMap<>();
    public Map<String,String> attributeOverlayRarity = new HashMap<>();
    public Map<String,String> attributeOverlayNames = new HashMap<>();
    public Map<String,Boolean> attributeOverlayEnabled = new HashMap<>();

    // ported from SkyHanni (LGPL-3.0-or-later): features/foraging/TreeProgressDisplay.kt
    // ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/TreeBreakProgressHud.java
    public boolean treeProgress = true;
    public boolean treeProgressHud = true;
    public boolean treeProgressOnlyHoldingAxe = true;
    public boolean treeProgressOnlyOwn = false;
    public boolean treeProgressCompact = false;
    public boolean treeProgressShowType = true;
    public boolean treeProgressShowPercent = true;
    public boolean treeProgressShowBar = true;
    public boolean treeProgressShowDistance = false;
    public boolean treeProgressShowContributors = false;
    public boolean treeProgressCompletionAlert = false;
    public boolean treeProgressCompletionChat = false;
    public boolean treeProgressCompletionTitle = false;
    public boolean treeProgressCompletionSound = true;
    public int treeProgressScanRange = 96;
    public int treeProgressAlertPercent = 100;
    public int treeProgressNormalColor = 0xFF55FF55;
    public int treeProgressNearlyDoneColor = 0xFFFFFF55;
    public int treeProgressCompleteColor = 0xFFFFAA00;

    // ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/{ForestNodes,TerracottaPuzzle,AbstractBlockHighlighter,LushlilacHighlighter,SeaLumiesHighlighter}.java
    public boolean galateaExploration = true;
    public boolean galateaForestNodes = true;
    public boolean galateaNodeBox = true;
    public boolean galateaNodeLabel = true;
    public boolean galateaNodeBeam = false;
    public boolean galateaNodeLine = false;
    public boolean galateaNodeDistance = true;
    public boolean galateaTempleSolver = true;
    public boolean galateaTempleLabels = true;
    public boolean galateaTempleBoxes = true;
    public boolean galateaTempleHideSolved = true;
    public boolean galateaLushlilac = true;
    public boolean galateaSeaLumies = true;
    public boolean galateaResourceBoxes = true;
    public boolean galateaResourceLabels = false;
    public boolean galateaResourceBeams = false;
    public boolean galateaResourceDistance = true;
    public boolean galateaExplorationThroughWalls = true;
    public int galateaSeaLumiesMinimum = 3;
    public int galateaExplorationRange = 64;
    public int galateaExplorationChunkRadius = 4;
    public int galateaNodeBeamHeight = 8;
    public int galateaResourceBeamHeight = 5;
    public int galateaNodeColor = 0xFFFFAA00;
    public int galateaTempleClockwiseColor = 0xFF55FF55;
    public int galateaTempleCounterColor = 0xFFFF5555;
    public int galateaLushlilacColor = 0xFFFF55FF;
    public int galateaSeaLumiesColor = 0xFF55FFFF;

    // ported from Skyblocker (LGPL-3.0-only): skyblock/foraging/SweepOverlay.java
    // ported from Skyblocker (LGPL-3.0-only): skyblock/galatea/{SweepDetailsListener,SweepDetailsHudWidget}.java
    // ported from SkyHanni (LGPL-3.0-or-later): features/foraging/CompactSweepDetails.kt
    public boolean sweepHelper = true;
    public boolean sweepBlockOverlay = true;
    public boolean sweepThrownOverlay = true;
    public boolean sweepRespectThrownCooldown = true;
    public boolean sweepShowTarget = true;
    public boolean sweepShowCountLabel = true;
    public boolean sweepShowToughness = false;
    public boolean sweepThroughWalls = false;
    public boolean sweepMissingStatNotice = true;
    public boolean sweepDetailsHud = true;
    public boolean sweepDetailsCompactChat = true;
    public boolean sweepDetailsShowTree = true;
    public boolean sweepDetailsShowToughness = true;
    public boolean sweepDetailsShowSweep = true;
    public boolean sweepDetailsShowLogs = true;
    public boolean sweepDetailsShowPenalty = true;
    public boolean sweepDetailsShowCorrectStyle = true;
    public boolean sweepDetailsShowInactive = false;
    public int sweepMaximumLogs = 35;
    public int sweepThrownRange = 50;
    public int sweepDetailsVisibleMillis = 1000;
    public int sweepOverlayColor = 0x66FF9600;
    public int sweepThrownColor = 0x6655AAFF;
    public int sweepTargetColor = 0xFFFFFF55;
    public int sweepGoodColor = 0xFF55FF55;
    public int sweepPenaltyColor = 0xFFFF5555;

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/foraging/MoongladeBeaconConfig.kt
    public boolean moongladeBeacon = true;
    public boolean moongladeBeaconHud = true;
    public boolean moongladeBeaconUseMiddleClick = true;
    public boolean moongladeBeaconPreventOverClicking = true;
    public boolean moongladeBeaconControlBypass = true;
    public boolean moongladeBeaconHighlightCorrect = true;
    public boolean moongladeBeaconOffsetLabels = true;
    public boolean moongladeBeaconShowReference = true;
    public boolean moongladeBeaconShowCurrent = true;
    public boolean moongladeBeaconShowOffsets = true;
    public boolean moongladeBeaconShowSolved = true;
    public boolean moongladeBeaconStereoWarning = true;
    public boolean moongladeBeaconReadyAlert = false;
    public boolean moongladeBeaconReadyChat = false;
    public boolean moongladeBeaconReadyTitle = false;
    public boolean moongladeBeaconReadySound = true;
    public int moongladeBeaconCorrectColor = 0xAA55FF55;
    public int moongladeBeaconUnknownColor = 0xFFFFFF55;
    public int moongladeBeaconReadyColor = 0xFF55FF55;
    public int moongladeBeaconPitchToleranceMillis = 150;
    public int moongladeBeaconSpeedSamples = 10;

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/foraging/HotfConfig.kt
    public boolean hotfHelper = true;
    public boolean hotfHighlightEnabledPerks = true;
    public boolean hotfLevelStackSize = true;
    public boolean hotfTokenStackSize = true;
    public boolean hotfWhispersSpent = true;
    public boolean hotfWhispersFor10Levels = true;
    public boolean hotfCurrentWhispers = true;
    public boolean hotfHud = true;
    public boolean hotfHudTokens = true;
    public boolean hotfHudWhispers = true;
    public boolean hotfHudSpent = true;
    public boolean hotfHudPerks = true;
    public boolean hotfHudMaxed = true;
    public boolean hotfHideMaxedTooltipDetails = false;
    public int hotfEnabledColor = 0x8855FF55;
    public int hotfDisabledColor = 0x88FF5555;
    public int hotfLockedColor = 0x88666666;
    public int hotfMaxLevelTextColor = 0xFFFFAA00;
    public int hotfLevelTextColor = 0xFFFFFF55;
    public int hotfTokenTextColor = 0xFF55FFFF;
    public String hotfWhispersSpentDesign = "NUMBER_AND_PERCENTAGE";

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/foraging/ForagingTrackerConfig.kt
    public boolean foragingTracker = true;
    public boolean foragingTrackerHud = true;
    public boolean foragingTrackerCompactGiftChat = true;
    public boolean foragingTrackerCompactUncommon = true;
    public boolean foragingTrackerCompactBooks = true;
    public boolean foragingTrackerCompactMobs = true;
    public boolean foragingTrackerCompactBoosters = true;
    public boolean foragingTrackerCompactShards = true;
    public boolean foragingTrackerCompactRunes = true;
    public boolean foragingTrackerCompactMisc = true;
    public boolean foragingTrackerOnlyHoldingAxe = true;
    public boolean foragingTrackerShowWholeTrees = true;
    public boolean foragingTrackerShowTable = true;
    public boolean foragingTrackerShowRecent = true;
    public boolean foragingTrackerShowProfit = true;
    public boolean foragingTrackerShowProfitPerHour = true;
    public boolean foragingTrackerShowForagingXp = true;
    public boolean foragingTrackerShowHotfXp = true;
    public boolean foragingTrackerShowWhispers = true;
    public boolean foragingTrackerShowTrees = true;
    public boolean foragingTrackerShowUptime = true;
    public boolean foragingTrackerPersistent = false;
    public boolean foragingTrackerTrackInventoryLogs = true;
    public boolean foragingTrackerChatWarning = true;
    public boolean foragingTrackerTitleWarning = true;
    public boolean foragingTrackerSoundWarning = true;
    public int foragingTrackerDisappearSeconds = 15;
    public int foragingTrackerRecentSeconds = 15;
    public int foragingTrackerAfkSeconds = 60;
    public int foragingTrackerRows = 10;
    public int foragingTrackerMinimumChatMillions = 5;
    public int foragingTrackerMinimumTitleMillions = 5;
    public int foragingTrackerNameColor = 0xFFFFFFFF;
    public int foragingTrackerValueColor = 0xFFFFAA00;
    public int foragingTrackerRecentColor = 0xFF55FF55;
    public String foragingTrackerPriceSource = "PURCHASE";
    public String foragingTrackerSort = "VALUE_DESC";
    public String foragingTrackerTreeFilter = "ALL";
    public Map<String,Long> foragingTrackerAmounts = new HashMap<>();
    public Map<String,String> foragingTrackerNames = new HashMap<>();
    public Map<String,Long> foragingTrackerLastGains = new HashMap<>();
    public Map<String,Long> foragingTrackerTrees = new HashMap<>();
    public Map<String,Double> foragingTrackerWholeTrees = new HashMap<>();
    public Map<String,Long> foragingTrackerForagingXp = new HashMap<>();
    public Map<String,Long> foragingTrackerHotfXp = new HashMap<>();
    public Map<String,Long> foragingTrackerWhispers = new HashMap<>();
    public Map<String,Long> foragingTrackerUptime = new HashMap<>();

    // ported from SkyHanni (LGPL-3.0-or-later): config/features/foraging/StarlynContestsConfig.kt
    public boolean starlynCouponProfit = true;
    public boolean starlynCouponProfitHud = true;
    public boolean starlynCouponHighlightSlots = true;
    public boolean starlynCouponSlotLabels = true;
    public boolean starlynCouponTooltips = true;
    public boolean starlynCouponShowItem = true;
    public boolean starlynCouponShowSell = true;
    public boolean starlynCouponShowCost = true;
    public boolean starlynCouponShowProfit = true;
    public boolean starlynCouponShowCouponCount = true;
    public boolean starlynCouponShowNegative = true;
    public boolean starlynCouponHideUnpriced = false;
    public boolean starlynCompactResults = false;
    public boolean starlynCompactPersonalBest = false;
    public int starlynCouponRows = 20;
    public int starlynCouponPositiveColor = 0x8855FF55;
    public int starlynCouponNegativeColor = 0x88FF5555;
    public int starlynCouponUnknownColor = 0x88777777;
    public int starlynCouponManualPrice = 0;
    public String starlynCouponOutputPriceSource = "SELL";
    public String starlynCouponInputPriceSource = "PURCHASE";
    public String starlynCouponSort = "PROFIT_DESC";
}
