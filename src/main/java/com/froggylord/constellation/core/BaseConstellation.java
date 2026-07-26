package com.froggylord.constellation.core;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.BaseConfigGroup;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.render.WorldRenderer;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class BaseConstellation {

    protected BaseConfigGroup config;
    private boolean enabled = true;
    private boolean initialized = false;
    private final List<Consumer<WorldRenderer.Ctx>> renderers = new ArrayList<>();
    private final List<WorldRenderer.Handle> rendererHandles = new ArrayList<>();
    private final List<TickTask> tickTasks = new ArrayList<>();
    private final List<String> scheduledTasks = new ArrayList<>();

    private record TickTask(int interval, String id, Runnable runnable) {}

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
        if (enabled) return;
        enabled = true;
        if (config != null) config.enabled = true;
        for (Consumer<WorldRenderer.Ctx> renderer : renderers)
            rendererHandles.add(ConstellationClient.world().register(renderer));
        for (TickTask task : tickTasks)
            ConstellationClient.tick().every(task.interval(), task.id(), task.runnable());
        onEnable();
    }

    public final void disable() {
        if (!enabled) return;
        enabled = false;
        if (config != null) config.enabled = false;
        for (WorldRenderer.Handle handle : rendererHandles) ConstellationClient.world().remove(handle);
        rendererHandles.clear();
        for (TickTask task : tickTasks) ConstellationClient.tick().cancel(task.id());
        for (String id : scheduledTasks) ConstellationClient.tick().cancel(id);
        scheduledTasks.clear();
        onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}

    public boolean isEnabled() { return enabled; }
    public boolean isInitialized() { return initialized; }
    public void markInitialized() { this.initialized = true; }

    protected final void registerRenderer(Consumer<WorldRenderer.Ctx> renderer) {
        renderers.add(renderer);
        if (enabled) rendererHandles.add(ConstellationClient.world().register(renderer));
    }

    protected final void every(int interval, String id, Runnable runnable) {
        tickTasks.add(new TickTask(interval, id, runnable));
        if (enabled) ConstellationClient.tick().every(interval, id, runnable);
    }

    protected final void schedule(int delay, String id, Runnable runnable) {
        if (!enabled) return;
        scheduledTasks.add(id);
        ConstellationClient.tick().once(delay, id, () -> {
            scheduledTasks.remove(id);
            if (isEnabled()) runnable.run();
        });
    }
}
