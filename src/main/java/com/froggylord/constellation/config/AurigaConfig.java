package com.froggylord.constellation.config;

/** Config for the Auriga constellation (experiments + enchanting + misc). */
public class AurigaConfig extends BaseConfigGroup {
    @Override public int currentVersion() { return 0; }
    public boolean experimentHelper = true;

    // experiment-table solvers — highlight only
    public boolean ultrasequencerSolver = true;
    public boolean superpairsSolver = true;
}
