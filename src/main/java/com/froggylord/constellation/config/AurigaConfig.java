package com.froggylord.constellation.config;

public class AurigaConfig extends BaseConfigGroup {
    { enabled = false; }
    @Override public int currentVersion() { return 0; }

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/experiment/ExperimentSolver.java
    public boolean experimentSolver = true;
    public boolean chronomatronSolver = true;
    public boolean ultrasequencerSolver = true;
    public boolean superpairsSolver = true;
    public boolean experimentHud = true;
    public boolean experimentHypixelOnly = true;
    public boolean experimentPrivateIslandOnly = true;
    public boolean experimentBlockIncorrectClicks = true;
    public boolean experimentControlBypass = true;
    public boolean experimentBlockEarlyClicks = true;
    public boolean experimentHideTooltips = false;
    public boolean experimentShowNext = true;
    public boolean experimentShowSecond = true;
    public boolean experimentShowRemaining = true;
    public boolean experimentDimWrong = true;
    public boolean experimentShowNumbers = true;
    public boolean superpairsRememberItems = true;
    public boolean superpairsHighlightKnownPairs = true;
    public boolean superpairsHighlightCurrentMatch = true;
    public boolean superpairsHighlightPowerups = true;
    public boolean superpairsBlockKnownWrongSecond = true;
    public int experimentNextColor = 0xA055FF55;
    public int experimentSecondColor = 0xA0FFAA00;
    public int experimentLaterColor = 0x80FF5555;
    public int experimentWrongColor = 0x90000000;
    public int superpairsKnownPairColor = 0x8055FFFF;
    public int superpairsCurrentMatchColor = 0x8055FF55;
    public int superpairsPowerupColor = 0x80FF55FF;

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/chocolatefactory/ChocolateFactorySolver.java, TimeTowerReminder.java
    public boolean chocolateFactoryHelper = true;
    public boolean chocolateFactoryHud = true;
    public boolean chocolateFactoryBestUpgrade = true;
    public boolean chocolateFactoryBestAffordable = true;
    public boolean chocolateFactoryUpgradeTimers = true;
    public boolean chocolateFactoryPayback = true;
    public boolean chocolateFactoryPrestige = true;
    public boolean chocolateFactoryStrayRabbits = true;
    public boolean chocolateFactoryStraySound = true;
    public boolean chocolateFactoryGoldenSound = true;
    public boolean chocolateFactoryTimeTower = true;
    public boolean chocolateFactoryTimeTowerChat = true;
    public boolean chocolateFactoryTimeTowerTitle = false;
    public boolean chocolateFactoryTimeTowerSound = true;
    public boolean chocolateFactoryShowHitman = true;
    public boolean chocolateFactoryShowLevels = true;
    public int chocolateFactoryWarningMinutes = 5;
    public int chocolateFactoryBestColor = 0x8055FFFF;
    public int chocolateFactoryAffordableColor = 0x8055FF55;
    public int chocolateFactoryUnaffordableColor = 0x80FFFF55;
    public int chocolateFactoryPrestigeColor = 0x8055FF55;
    public int chocolateFactoryStrayColor = 0x80FF55FF;
    public java.util.Map<String, Long> chocolateFactoryTimeTowerExpiry = new java.util.LinkedHashMap<>();

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/item/AnvilHelper.java
    // ported from NoFrills (GPL-3.0-only): features/solvers/AnvilHelper.java
    public boolean anvilHelper = true;
    public boolean anvilHud = true;
    public boolean anvilMismatchWarning = true;
    public boolean anvilBlockMismatchOutput = true;
    public boolean anvilControlBypass = true;
    public boolean anvilMismatchSound = true;
    public boolean anvilHighlightInputs = true;
    public boolean anvilHighlightResult = true;
    public boolean anvilFindMatchingBooks = true;
    public boolean anvilMatchPlayerInventoryOnly = true;
    public boolean anvilMatchExactLevel = true;
    public boolean anvilShowTooltip = true;
    public boolean anvilShowEnchantments = true;
    public int anvilInputColor = 0x8055FFFF;
    public int anvilMatchColor = 0x8055FF55;
    public int anvilMismatchColor = 0x80FF5555;
    public int anvilResultColor = 0x8055FF55;

    // ported from Skyblocker (LGPL-3.0-or-later): skyblock/tabhud/widget/EffectWidget.java
    // ported from SkyblockAddons (LGPL-3.0-only): features/tablist/TabListParser.java, resources/regex.json
    public boolean buffStatus = true;
    public boolean buffStatusHud = true;
    public boolean buffStatusGodPotion = true;
    public boolean buffStatusCookie = true;
    public boolean buffStatusEffectCount = true;
    public boolean buffStatusShowUnknown = false;
    public boolean buffStatusShowExpired = true;
    public boolean buffStatusShowSourceAge = false;
    public boolean buffStatusShowProfile = false;
    public boolean buffStatusGodWarning = true;
    public boolean buffStatusGodExpired = true;
    public boolean buffStatusCookieWarning = true;
    public boolean buffStatusCookieExpired = true;
    public boolean buffStatusWarningChat = true;
    public boolean buffStatusExpiredChat = true;
    public boolean buffStatusWarningTitle = false;
    public boolean buffStatusExpiredTitle = false;
    public boolean buffStatusWarningSound = true;
    public boolean buffStatusExpiredSound = true;
    public int buffStatusGodWarningMinutes = 30;
    public int buffStatusCookieWarningHours = 24;
    public int buffStatusActiveColor = 0xFF55FF55;
    public int buffStatusWarningColor = 0xFFFFAA00;
    public int buffStatusExpiredColor = 0xFFFF5555;
    public int buffStatusUnknownColor = 0xFFAAAAAA;
    public java.util.Map<String, Long> buffStatusGodExpiry = new java.util.LinkedHashMap<>();
    public java.util.Map<String, Long> buffStatusCookieExpiry = new java.util.LinkedHashMap<>();
}
