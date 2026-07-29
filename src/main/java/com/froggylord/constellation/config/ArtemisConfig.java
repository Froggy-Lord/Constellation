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
}
