package com.froggylord.constellation.config;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseConfigGroup {

    public boolean enabled = true;

    public int version = 0;

    public Map<String, Boolean> subOptions = new HashMap<>();

    public boolean getSub(String key, boolean def) {
        return subOptions.getOrDefault(key, def);
    }

    public void setSub(String key, boolean val) {
        subOptions.put(key, val);
    }

    public final void checkMigration() {
        int target = currentVersion();
        if (version < target) {
            migrate(version);
            version = target;
        }
    }

    public int currentVersion() { return 0; }

    public void migrate(int fromVersion) {}
}
