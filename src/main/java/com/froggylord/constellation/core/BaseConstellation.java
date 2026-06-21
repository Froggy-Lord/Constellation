package com.froggylord.constellation.core;

import com.froggylord.constellation.config.BaseConfigGroup;
import com.froggylord.constellation.hud.HudManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Base class for every constellation module.
 * Each constellation is a self-contained feature set with its own config section.
 */
public abstract class BaseConstellation {

    protected BaseConfigGroup config;
    private boolean enabled = true;

    /** Unique identifier, e.g. "apollo", "orion" */
    public abstract String id();

    /** Display name shown in UI, e.g. "Apollo", "Orion" */
    public abstract String displayName();

    /** Short description for the hub card */
    public abstract String description();

    /** Hard dependencies: constellation IDs that must be loaded before this one */
    public String[] requires() { return null; }

    /** Called during FEATURES phase. Register event listeners, packet handlers, etc. */
    public void init(InitContext ctx) {}

    /** Called during HUD phase. Register HudElements. */
    public void registerHud(HudManager hud) {}

    /** Called during COMMANDS phase. Register Brigadier commands. */
    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {}

    /** Called when config is reloaded */
    public void onConfigReload() {}

    // lifecycle

    public final void setConfig(BaseConfigGroup cfg) {
        this.config = cfg;
        this.enabled = cfg.enabled;
    }

    public final BaseConfigGroup getConfig() { return config; }

    public final void enable() {
        enabled = true;
        if (config != null) config.enabled = true;
        onEnable();
    }

    public final void disable() {
        enabled = false;
        if (config != null) config.enabled = false;
        onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public boolean isEnabled() { return enabled; }
}
