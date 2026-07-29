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
}
