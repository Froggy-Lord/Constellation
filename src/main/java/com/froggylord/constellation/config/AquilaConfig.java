package com.froggylord.constellation.config;

/** Config for the Aquila constellation (mining). */
public class AquilaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean powderHud = true;
    public boolean commissionHud = true;
    public boolean coldWarning = true;   // cold title pings
    public boolean coldHud = true;
    public boolean mineshaftAlert = true;
    public boolean hotmHud = true;
    public boolean drillFuelHud = true;      // drill fuel
    public boolean pickonimbusHud = true;    // pickonimbus uses
    public boolean fetchurSolver = true;     // fetchur hints
    public boolean puzzlerSolver = true;     // puzzler answers
    public boolean scathaAlert = true;       // scatha alert
    public boolean pickobulusPreview = true; // pickobulus preview
    public boolean treasureChestEsp = true;   // chest esp
    public boolean wishingCompassHelper = true; // compass helper
    public boolean nucleusHelper = true;  // nucleus crystals
    public boolean templePearlHelper = true; // temple pearl
    public boolean scathaCounter = true;      // scatha kills
    public boolean yolkarSpeedup = true;       // yolkar skip
    public boolean goldenGoblinAlert = true;   // gold goblin
    public boolean gemstoneDesyncFix = true;   // gem desync fix
    public boolean jadeCrystalTracker = true;  // jade crystal
    public boolean coleweightHud = true;         // coleweight hud
    public boolean fossilHelper = true;          // fossil helper
    public boolean crystalNucleusWaypoints = true; // crystal waypoints (cmp. Skyblocker)
    public boolean metalDetectorHelper = true;    // metal detector (cmp. Skyblocker)
    public boolean gemstoneMixtureHelper = true;  // gem mixture (cmp. Skyblocker)
    public boolean mineshaftPityDisplay = true;  // mineshaft pity (cmp. Skyblocker)
}
