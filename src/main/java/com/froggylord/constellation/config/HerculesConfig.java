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
}
