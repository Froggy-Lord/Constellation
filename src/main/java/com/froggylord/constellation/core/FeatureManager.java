package com.froggylord.constellation.core;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.BaseConfigGroup;
import com.froggylord.constellation.constellation.*;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.*;

public class FeatureManager {

    private final List<BaseConstellation> constellations = new ArrayList<>();
    private final Set<String> loaded = new LinkedHashSet<>();

    public void discoverAndInit() {
        register(new ApolloHud());
        register(new CassiopeiaChat());
        register(new PhoenixQol());

        for (BaseConstellation c : constellations) {
            if (c.getConfig() != null && c.getConfig().enabled) {
                InitContext ctx = new InitContext();
                c.init(ctx);
                c.markInitialized();
                loaded.add(c.id());
                ConstellationClient.LOGGER.info("  [{}] loaded", c.id());
            } else {
                ConstellationClient.LOGGER.info("  [{}] disabled by config", c.id());
            }
        }
    }

    private void register(BaseConstellation c) {
        if (c.requires() != null) {
            for (String req : c.requires()) {
                boolean found = constellations.stream().anyMatch(r -> r.id().equals(req));
                if (!found) {
                    ConstellationClient.LOGGER.warn("Constellation '{}' requires '{}' which is not loaded. Skipping.", c.id(), req);
                    return;
                }
            }
        }
        BaseConfigGroup cfg = ConstellationClient.instance().configManager().getGroup(c.id());
        if (cfg != null) c.setConfig(cfg);
        constellations.add(c);
    }

    public void registerHudElements() {
        for (BaseConstellation c : constellations) {
            if (loaded.contains(c.id())) {
                c.registerHud(ConstellationClient.hudManager());
            }
        }
    }

    public void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        for (BaseConstellation c : constellations) {
            if (loaded.contains(c.id())) {
                c.registerCommands(dispatcher);
            }
        }
    }

    public int getLoadedCount() { return loaded.size(); }
    public Set<String> getLoadedIds() { return loaded; }
    public List<String> getAllIds() {
        return constellations.stream().map(BaseConstellation::id).toList();
    }

    public Optional<BaseConstellation> get(String id) {
        return constellations.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public void toggle(String id) {
        get(id).ifPresent(c -> {
            if (loaded.contains(id)) {
                // disable: unregister HUD, mark config
                c.disable();
                loaded.remove(id);
                ConstellationClient.hudManager().getAll().removeIf(el -> el.id().startsWith(id + "-"));
            } else {
                // enable: init if not already done, register HUD
                if (!c.isInitialized()) {
                    c.init(new InitContext());
                    c.markInitialized();
                }
                c.enable();
                loaded.add(id);
                c.registerHud(ConstellationClient.hudManager());
            }
            c.getConfig().enabled = loaded.contains(id);
            ConstellationClient.saveConfig();
        });
    }
}
