package com.froggylord.constellation.config;

/**
 * Base class for per-constellation config groups.
 * Every constellation gets a subclass with its own fields.
 */
public abstract class BaseConfigGroup {

    /** Master toggle for this constellation */
    public boolean enabled = true;

    /** Version of this config group for individual migration */
    public int version = 0;

    /**
     * Check if this group needs migration and apply it.
     * Called by ConfigManager after deserializing.
     */
    public final void checkMigration() {
        int target = currentVersion();
        if (version < target) {
            migrate(version);
            version = target;
        }
    }

    /** Override to declare the current version for this config group */
    public int currentVersion() { return 0; }

    /** Override to handle version-to-version migrations */
    public void migrate(int fromVersion) {}
}
