package com.froggylord.constellation.config;

/** Config for the Aquila constellation (mining). */
public class AquilaConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean powderHud = true;
    public boolean commissionHud = true;
    public boolean coldWarning = true;   // title pings at cold thresholds (no vignette)
    public boolean coldHud = true;
    public boolean mineshaftAlert = true;
    public boolean hotmHud = true;
    public boolean drillFuelHud = true;      // drill fuel remaining (Skyblocker: MiningHud)
    public boolean pickonimbusHud = true;    // Pickonimbus durability (Skyblocker: MiningHud)
    public boolean fetchurSolver = true;     // Fetchur item hints (cmp. Skyblocker FetchurSolver)
    public boolean puzzlerSolver = true;     // Puzzler block answers (cmp. Skyblocker PuzzlerSolver)
    public boolean scathaAlert = true;       // Scatha spawn alert (cmp. Skyblocker ScathaTracker)
    public boolean pickobulusPreview = true; // highlight break area (cmp. Skyblocker PickobulusPredictor)
    public boolean treasureChestEsp = true;   // highlight chests in Crystal Hollows (cmp. Skyblocker ChestHighlighter)
    public boolean wishingCompassHelper = true; // track compass readings for triangulation (cmp. Skyblocker NucleusHelper)
    public boolean nucleusHelper = true;  // show which crystals you have/need (cmp. Skyblocker NucleusHelper)
    public boolean templePearlHelper = true; // pearl clip waypoint for temple skip
    public boolean scathaCounter = true;      // track scatha worm kills (cmp. Skyblocker ScathaTracker)
    public boolean yolkarSpeedup = true;       // skip King Yolkar dialogue (cmp. Skyblocker)
}
