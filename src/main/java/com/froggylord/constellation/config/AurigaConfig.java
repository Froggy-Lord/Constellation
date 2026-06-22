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
    public boolean essenceShopHelper = true; // show essence costs + upgrades (cmp. Skyblocker)
    public boolean reforgeHelper = true;     // show reforge stats comparison (cmp. Skyblocker)
    public boolean chocolateFactoryHelper = true; // Chocolate Factory event tracker (cmp. Skyblocker)
    public boolean minionHopperTracker = true;   // show minion hopper contents (cmp. Skyblocker)
    public boolean evolvingItemTimer = true;       // show evolving item time remaining (cmp. Skyblocker)
    public boolean attributeShardHelper = true;    // show attribute shard combine costs (cmp. Skyblocker)
    public boolean enchantTableHelper = true;       // show cheapest enchant path (cmp. Skyblocker)
    public boolean brewHelper = true;              // show potion recipes + best upgrades (cmp. Skyblocker)
    public boolean godPotDisplay = true;            // show active god pot + remaining time (cmp. Skyblocker)
    public boolean teleportPadHelper = true;        // highlight teleport pad destinations (cmp. Skyblocker)
}
