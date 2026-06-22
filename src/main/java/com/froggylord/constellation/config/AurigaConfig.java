package com.froggylord.constellation.config;

/** Config for the Auriga constellation (experiments + enchanting + misc). */
public class AurigaConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean experimentHelper = true;

    // experiment-table solvers — highlight only
    public boolean ultrasequencerSolver = true;
    public boolean superpairsSolver = true;
    public boolean bingoHelper = true;
    public boolean shCalcCommand = true; // /shcalc damage estimator
    public boolean anvilHelper = true;    // show combine cost + optimal path (cmp. Skyblocker AnvilHelper)
    public boolean powerStoneDisplay = true; // show active power stone from sidebar
    public boolean clockReminder = true;     // enchanted clock event reminders (cmp. Skyblocker)
}
