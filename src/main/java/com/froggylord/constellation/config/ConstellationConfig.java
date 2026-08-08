package com.froggylord.constellation.config;

import com.google.gson.annotations.SerializedName;
import com.froggylord.constellation.hud.HudPosition;

public class ConstellationConfig {

    public static final int CURRENT_VERSION = 3;

    public int cfgVersion = CURRENT_VERSION;

    
    
    
    public boolean lifetimeStats = true;
    public boolean verifyMode = false;
    public java.util.Map<String, Float> hudScales = new java.util.HashMap<>();
    public java.util.Map<String, HudPosition> hudPositions = new java.util.HashMap<>();
    @SerializedName("visual") public VisualConfig visual = new VisualConfig();

    
    @SerializedName("apollo")     public ApolloConfig apollo = new ApolloConfig();
    @SerializedName("cassiopeia") public CassiopeiaConfig cassiopeia = new CassiopeiaConfig();
    @SerializedName("orion")      public OrionConfig orion = new OrionConfig();
    @SerializedName("phoenix")    public PhoenixConfig phoenix = new PhoenixConfig();
    @SerializedName("aquila")     public AquilaConfig aquila = new AquilaConfig();
    @SerializedName("lyra")       public LyraConfig lyra = new LyraConfig();
    @SerializedName("cygnus")     public CygnusConfig cygnus = new CygnusConfig();
    @SerializedName("hercules")   public HerculesConfig hercules = new HerculesConfig();
    @SerializedName("draco")      public DracoConfig draco = new DracoConfig();
    @SerializedName("hydra")      public HydraConfig hydra = new HydraConfig();
    @SerializedName("perseus")    public PerseusConfig perseus = new PerseusConfig();
    @SerializedName("andromeda")  public AndromedaConfig andromeda = new AndromedaConfig();
    @SerializedName("pegasus")    public PegasusConfig pegasus = new PegasusConfig();
    @SerializedName("auriga")     public AurigaConfig auriga = new AurigaConfig();
    @SerializedName("artemis")    public ArtemisConfig artemis = new ArtemisConfig();

    public boolean migrate() {
        boolean changed = false;
        if (visual == null) { visual = new VisualConfig(); changed = true; }
        if (cfgVersion < CURRENT_VERSION) {
            migrateFrom(cfgVersion);
            cfgVersion = CURRENT_VERSION;
            changed = true;
        }
        for (BaseConfigGroup group : java.util.List.of(visual, apollo, cassiopeia, orion, phoenix, aquila,
            lyra, cygnus, hercules, draco, hydra, perseus, andromeda, pegasus, auriga, artemis)) {
            int before = group.version;
            group.checkMigration();
            changed |= before != group.version;
        }
        return changed;
    }

    private void migrateFrom(int fromVersion) {
        if (fromVersion < 1) {
            pegasus.enabled = true;
            pegasus.carryMode = true;
        }
        if (fromVersion < 2) perseus.cocoonAlert = hydra.legacyCocoonAlert;
    }

    public BaseConfigGroup getSubConfig(String id) {
        return switch (id) {
            case "apollo"     -> apollo;
            case "cassiopeia" -> cassiopeia;
            case "orion"      -> orion;
            case "phoenix"    -> phoenix;
            case "aquila"     -> aquila;
            case "lyra"       -> lyra;
            case "cygnus"     -> cygnus;
            case "hercules"   -> hercules;
            case "draco"      -> draco;
            case "hydra"      -> hydra;
            case "perseus"    -> perseus;
            case "andromeda"  -> andromeda;
            case "pegasus"    -> pegasus;
            case "auriga"     -> auriga;
            case "artemis"    -> artemis;
            case "visual"     -> visual;
            default -> null;
        };
    }
}
