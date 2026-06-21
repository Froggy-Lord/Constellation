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
        register(new PhoenixQol());

        for (BaseConstellation c : constellations) {
            InitContext ctx = new InitContext();
            c.init(ctx);
            loaded.add(c.id());
            ConstellationClient.LOGGER.info("  [{}] loaded", c.id());
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
        if (cfg != null) {
            c.setConfig(cfg);
            if (!cfg.enabled) {
                ConstellationClient.LOGGER.info("  [{}] disabled by config", c.id());
                return;
            }
        }
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

    public Optional<BaseConstellation> get(String id) {
        return constellations.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public void toggle(String id) {
        get(id).ifPresent(c -> {
            if (loaded.contains(id)) {
                c.disable();
                loaded.remove(id);
            } else {
                c.enable();
                loaded.add(id);
            }
        });
    }
}
