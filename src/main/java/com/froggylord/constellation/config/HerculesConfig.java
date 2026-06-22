package com.froggylord.constellation.config;

/** Config for the Hercules constellation (farming + garden). */
public class HerculesConfig extends BaseConfigGroup {

    @Override public int currentVersion() { return 0; }

    public boolean contestHud = true;
    public boolean visitorsHud = false;
    public boolean pestHud = true;
    public boolean pestAlert = true;
    public boolean cropMilestones = true;
    public boolean visitorRequirements = true; // show what each visitor is offering
    public boolean composterHud = true; // organic matter + fuel display
    public boolean speedHud = true; // Rancher's Boots speed cap
    public boolean spaceFarmer = false; // hold space to auto-farm (cmp. SkyHanni)
    public boolean plotBorders = true; // render garden plot borders (cmp. SkyHanni)
    public boolean sweepOverlay = true; // show farming tool harvest range (cmp. SkyHanni)
    public boolean dicerFilter = true;  // suppress verbose dicer messages (cmp. SkyHanni)
    public boolean moongladeBeacon = true; // show Moonglade Beacon level (cmp. SkyHanni)
    public boolean greenhouseHelper = true; // greenhouse paste preview
    public boolean stereoHarmonyHelper = true; // harp note guide (cmp. SkyHanni)
    public boolean veridianHelper = true; // Veridian area farming hints (cmp. SkyHanni)
    public boolean yawPitchLock = true;   // lock yaw/pitch for perfect farming rows (cmp. SkyHanni)
}
