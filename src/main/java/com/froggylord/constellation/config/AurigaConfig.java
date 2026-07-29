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
}
