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
}
