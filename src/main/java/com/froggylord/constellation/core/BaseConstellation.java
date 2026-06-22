package com.froggylord.constellation.core;

import com.froggylord.constellation.config.BaseConfigGroup;
import com.froggylord.constellation.hud.HudManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public abstract class BaseConstellation {

    protected BaseConfigGroup config;
    private boolean enabled = true;
    private boolean initialized = false;

    public abstract String id();

    public abstract String displayName();

    public abstract String description();

    public String[] requires() { return null; }

    public void init(InitContext ctx) {}

    public void registerHud(HudManager hud) {}

    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {}

    public void onConfigReload() {}

    

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
    public boolean isInitialized() { return initialized; }
    public void markInitialized() { this.initialized = true; }
}
