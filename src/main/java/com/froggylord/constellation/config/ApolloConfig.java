package com.froggylord.constellation.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApolloConfig extends BaseConfigGroup {

    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    // ported from Devonian (GPL-3.0-only): features/misc/FPSDisplay.kt, PingDisplay.kt, TpsDisplay.kt, SpeedDisplay.kt
    public boolean performanceHud = true;
    public boolean performanceShowFps = true;
    public boolean performanceShowFpsAverage = true;
    public boolean performanceShowPing = true;
    public boolean performanceShowPingAverage = false;
    public boolean performanceShowPingMedian = true;
    public boolean performanceShowTpsCurrent = true;
    public boolean performanceShowTpsAverage = true;
    public boolean performanceShowTpsMinimum = false;
    public boolean performanceShowTpsMaximum = false;
    public boolean performanceHypixelOnly = false;
    public int performanceSampleSeconds = 5;
    public int performanceGoodFps = 60;
    public int performanceGoodPing = 100;
    public int performanceGoodTpsHundredths = 1900;
    public int performanceGoodColor = 0xFF55FF55;
    public int performanceWarningColor = 0xFFFFFF55;
    public int performanceBadColor = 0xFFFF5555;

    public boolean locationHud = true;
    public boolean locationShowCoordinates = true;
    public boolean locationDecimalCoordinates = false;
    public boolean locationShowFacing = true;
    public boolean locationShowYaw = false;
    public boolean locationShowPitch = false;
    public boolean locationShowDimension = false;
    public boolean locationShowLocalClock = true;
    public boolean locationTwelveHourClock = false;
    public boolean locationShowSeconds = false;
    public int locationCoordinateColor = 0xFF55FFFF;
    public int locationFacingColor = 0xFFFFFF55;
    public int locationClockColor = 0xFFFFFFFF;

    public boolean movementHud = true;
    public boolean movementShowSkyblockSpeed = true;
    public boolean movementShowBlocksPerSecond = true;
    public boolean movementShowVerticalSpeed = false;
    public boolean movementAverage = true;
    public int movementSampleTicks = 10;
    public int movementColor = 0xFF55FF55;

    public boolean vitalsHud = true;
    public boolean vitalsHypixelOnly = true;
    public boolean vitalsShowHealth = true;
    public boolean vitalsShowHealthPercent = false;
    public boolean vitalsShowMana = true;
    public boolean vitalsShowManaPercent = false;
    public boolean vitalsShowOverflowMana = false;
    public boolean vitalsShowDefense = true;
    public boolean vitalsShowEffectiveHealth = false;
    public int vitalsHealthColor = 0xFFFF5555;
    public int vitalsManaColor = 0xFF55FFFF;
    public int vitalsDefenseColor = 0xFF55FF55;

    public boolean effectsHud = true;
    public boolean effectsShowAmplifier = true;
    public boolean effectsShowDuration = true;
    public boolean effectsSortShortest = true;
    public boolean effectsHideInfinite = false;
    public int effectsMaxRows = 8;
    public int effectsWarningSeconds = 30;
    public int effectsActiveColor = 0xFF55FF55;
    public int effectsWarningColor = 0xFFFFFF55;
    public int effectsExpiredColor = 0xFFFF5555;

    // ported from CryptKit (GPL-3.0-only): config/CryptkitConfig.java Sidebar
    // cross-checked with SkyHanni (LGPL-3.0-or-later): config/features/gui/customscoreboard
    public boolean customScoreboard = true;
    public boolean customScoreboardHideVanilla = true;
    public boolean customScoreboardShowServerLines = true;
    public boolean customScoreboardUseCustomLines = true;
    public boolean customScoreboardBackground = true;
    public boolean customScoreboardOutline = false;
    public boolean customScoreboardTextShadow = false;
    public boolean customScoreboardHideEmptyLines = true;
    public boolean customScoreboardHideConsecutiveEmptyLines = true;
    public boolean customScoreboardHideEdgeEmptyLines = true;
    public boolean customScoreboardHideIrrelevantLines = true;
    public boolean customScoreboardUseCustomTitle = true;
    public boolean customScoreboardPersistValues = true;
    // ported from SkyHanni (LGPL-3.0-or-later): config/features/gui/customscoreboard
    public boolean customScoreboardShowDate = true;
    public boolean customScoreboardDateYear = false;
    public boolean customScoreboardShowTime = true;
    public boolean customScoreboardTime24Hour = false;
    public boolean customScoreboardTimeExactMinutes = false;
    public boolean customScoreboardShowLobby = true;
    public boolean customScoreboardLobbyRealDate = true;
    public boolean customScoreboardShowPlayers = true;
    public boolean customScoreboardShowMaxPlayers = true;
    public boolean customScoreboardShowEvents = true;
    public boolean customScoreboardShowAllEvents = true;
    public boolean customScoreboardShowUpcomingEvents = false;
    public boolean customScoreboardShowMayor = true;
    public boolean customScoreboardMayorPerks = true;
    public boolean customScoreboardMayorExtra = true;
    public boolean customScoreboardShowParty = true;
    public boolean customScoreboardPartyLeader = true;
    public boolean customScoreboardPartyMembers = true;
    public boolean customScoreboardShowFooter = true;
    public String customScoreboardTitle = "SKYBLOCK";
    public String customScoreboardFooter = "www.hypixel.net";
    public String customScoreboardLobbyDateFormat = "MM/dd/yy";
    public String customScoreboardTextAlignment = "LEFT";
    public String customScoreboardTitleAlignment = "CENTER";
    public int customScoreboardLineSpacing = 1;
    public int customScoreboardMaxRows = 40;
    public int customScoreboardMaxPlayers = 0;
    public int customScoreboardEventRows = 4;
    public int customScoreboardMayorPerkRows = 6;
    public int customScoreboardPartyRows = 4;
    public int customScoreboardBackgroundColor = 0xCC080810;
    public int customScoreboardOutlineColor = 0xFF555577;
    public int customScoreboardTitleColor = 0xFF55FFFF;
    public int customScoreboardServerColor = 0xFFDDDDDD;
    public int customScoreboardLabelColor = 0xFFAAAAAA;
    public int customScoreboardValueColor = 0xFFFFFFFF;
    public List<String> customScoreboardOrder = new ArrayList<>(List.of("lobby","date","time","players","server","bank","sbLevel","magicPower","tuning","powerStone","gems","quiver","godPot","events","mayor","party","election","area","purse","bits","footer"));
    public Set<String> customScoreboardHidden = new LinkedHashSet<>();
    public Map<String,String> customScoreboardCachedValues = new LinkedHashMap<>();
}
