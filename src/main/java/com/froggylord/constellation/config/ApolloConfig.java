package com.froggylord.constellation.config;

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
}
