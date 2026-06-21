package com.froggylord.constellation.config;

import com.google.gson.annotations.SerializedName;

/**
 * Root config POJO. Holds sub-configs for every constellation.
 * cfgVersion drives migration logic.
 */
public class ConstellationConfig {

    public static final int CURRENT_VERSION = 0;

    public int cfgVersion = CURRENT_VERSION;

    // sub-configs — one per constellation, named by their id
    @SerializedName("apollo")     public ApolloConfig apollo = new ApolloConfig();
    @SerializedName("cassiopeia") public CassiopeiaConfig cassiopeia = new CassiopeiaConfig();
    @SerializedName("orion")      public OrionConfig orion = new OrionConfig();
    @SerializedName("phoenix")    public PhoenixConfig phoenix = new PhoenixConfig();
    @SerializedName("aquila")     public AquilaConfig aquila = new AquilaConfig();
    @SerializedName("lyra")       public LyraConfig lyra = new LyraConfig();

    /**
     * Migrate from older config versions. Called after deserialization.
     */
    public void migrate() {
        if (cfgVersion < CURRENT_VERSION) {
            migrateFrom(cfgVersion);
            cfgVersion = CURRENT_VERSION;
        }
    }

    private void migrateFrom(int fromVersion) {
        // version-to-version migrations go here as the config evolves
    }

    /**
     * Get a sub-config by constellation id.
     */
    public BaseConfigGroup getSubConfig(String id) {
        return switch (id) {
            case "apollo"     -> apollo;
            case "cassiopeia" -> cassiopeia;
            case "orion"      -> orion;
            case "phoenix"    -> phoenix;
            case "aquila"     -> aquila;
            case "lyra"       -> lyra;
            default -> null;
        };
    }
}
