package com.froggylord.constellation.config;

/**
 * Config for the Phoenix constellation (QoL toggles).
 */
public class PhoenixConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean fullbright = true;
    public boolean noHurtCam = true;
    public boolean noViewBob = true;
    public boolean autoSprint = false;
    public boolean hideLightning = true;
    public boolean hideFallingBlocks = true;
    public boolean hideFireOverlay = true;
    public boolean hideUnderwaterBlur = false;
    public boolean hideStatusEffects = false;
    public boolean hidePlayersInDungeon = false;
    public boolean etherwarpOverlay = true;
    public boolean scrollableTooltips = true;
    public boolean preventPlacingWeapons = false;
    public boolean noDeathAnimation = false;
    public boolean disableVignette = false;
    public boolean disableFog = false;
    public boolean instantSneak = false;
    public boolean signCalculator = true;
}
