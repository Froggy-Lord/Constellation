package com.froggylord.constellation.constellation;

import com.froggylord.constellation.ConstellationClient;
import com.froggylord.constellation.config.HydraConfig;
import com.froggylord.constellation.core.BaseConstellation;
import com.froggylord.constellation.core.InitContext;
import com.froggylord.constellation.hud.HudManager;
import com.froggylord.constellation.hud.HudPosition;
import com.froggylord.constellation.hud.HudWidget;

/**
 * Hydra — fishing. Shows a simple "fishing" indicator when you're on a fishing-capable
 * island (any island with water/lava fishing). Sea creature specifics deferred — these need
 * exact chat patterns that vary by island.
 */
public class HydraFishing extends BaseConstellation {

    @Override public String id() { return "hydra"; }
    @Override public String displayName() { return "Hydra"; }
    @Override public String description() { return "Fishing — sea creatures, cast timer, trophy fish"; }

    private HydraConfig cfg;

    @Override
    public void init(InitContext ctx) { cfg = (HydraConfig) getConfig(); }

    @Override
    public void registerHud(HudManager hud) {
        cfg = (HydraConfig) getConfig();
        if (cfg == null) return;

        if (cfg.seaCreatureAlerts) {
            hud.register(new HudWidget("hydra-fishing", "Fishing",
                () -> ConstellationClient.loc().onHypixel() ? "§b🎣" : null,
                HudPosition.of(50, 86), cfg.seaCreatureAlerts));
        }
    }
}
