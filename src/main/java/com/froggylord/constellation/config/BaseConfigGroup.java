package com.froggylord.constellation.config;

import java.util.HashMap;
import java.util.Map;

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
     * Generic per-module sub-option storage, keyed "moduleName.optKey".
     * Lets every settings sub-toggle persist independently without a named field each.
     */
    public Map<String, Boolean> subOptions = new HashMap<>();

    public boolean getSub(String key, boolean def) {
        return subOptions.getOrDefault(key, def);
    }

    public void setSub(String key, boolean val) {
        subOptions.put(key, val);
    }

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
