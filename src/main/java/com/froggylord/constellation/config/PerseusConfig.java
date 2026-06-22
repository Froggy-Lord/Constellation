package com.froggylord.constellation.config;

public class PerseusConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean bossTimer = true;
    public boolean xpBar = true;
    public boolean slayerTimer = true; 
    public boolean brokenHyperionAlert = true; 
    public boolean skillLevelUpAlert = true;   
    public boolean bestiaryTracker = true;     
    public boolean rngMeterDetail = true;       
    public boolean rareDropEffect = true;        // special effect when rare drop ...
    public boolean vampireHelper = true;          
    public boolean endermanHelper = true;          
    public boolean blazeHelper = true;             
    public boolean spawnAlertTitle = true;          
    public boolean minibossFlash = true;            
    public boolean bestiaryMilestoneAlert = true;   // ping on bestiary milestone rea...
    public boolean slayerBossSpawnCustomSound = true; 
    public boolean sosFlareDisplay = true;       
    public boolean slayerProfitTracker = true;   // track coins gained per slayer ...
    public boolean tarantulaInvincMark = true;   // mark invincible tarantulas in ...
    public boolean hideIrrelevantMobs = true;      // hide non-slayer mobs during qu...
    public boolean damageIndicatorHud = true;
    public boolean autoSlayerHelper = true;
    public boolean bossBarImprovement = true;
}
